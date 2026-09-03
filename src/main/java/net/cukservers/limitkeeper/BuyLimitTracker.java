/*
 * Copyright (c) 2026, Corey Jenkins <https://github.com/CoreyUK>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.cukservers.limitkeeper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Counts how much of each item's buy limit the logged in account has used.
 *
 * <p>State lives in the RS profile config, one key per item and one per Grand Exchange slot, so
 * every account on the client is tracked separately and progress survives restarts.
 */
@Slf4j
@Singleton
class BuyLimitTracker
{
	/** A buy limit period runs for four hours from the first purchase of that item. */
	static final long WINDOW_MS = TimeUnit.HOURS.toMillis(4);

	private static final int SLOT_COUNT = 8;
	private static final String WINDOW_PREFIX = "window.";
	private static final String SLOT_PREFIX = "slot.";

	private final Client client;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Gson gson;

	/** Called on the client thread the first time a period reaches the item's limit. */
	@Setter
	private Consumer<BuyLimitWindow> limitReachedListener;

	@Inject
	private BuyLimitTracker(Client client, ConfigManager configManager, ItemManager itemManager, Gson gson)
	{
		this.client = client;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.gson = gson;
	}

	/**
	 * Folds one Grand Exchange offer update into the tracked totals. Must run on the client thread.
	 */
	void onOfferChanged(int slot, GrandExchangeOffer offer)
	{
		if (slot < 0 || slot >= SLOT_COUNT || configManager.getRSProfileKey() == null)
		{
			return;
		}

		final GrandExchangeOfferState state = offer.getState();

		if (state == GrandExchangeOfferState.EMPTY)
		{
			// The client wipes all offers while logging in or hopping. Keeping the snapshot through
			// that is what stops the next login from replaying finished offers as fresh purchases.
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				configManager.unsetRSProfileConfiguration(LimitKeeperConfig.GROUP, SLOT_PREFIX + slot);
			}
			return;
		}

		final SlotOffer previous = readSlot(slot);
		final SlotOffer current = new SlotOffer(offer.getItemId(), offer.getPrice(),
			offer.getTotalQuantity(), offer.getQuantitySold(), offer.getSpent());
		configManager.setRSProfileConfiguration(LimitKeeperConfig.GROUP, SLOT_PREFIX + slot, gson.toJson(current));

		if (!isBuy(state))
		{
			return;
		}

		// A slot holding an offer we have not seen before may already be partly filled - it was
		// bought while we were not watching, so count all of it now.
		final boolean sameOffer = current.isSameOffer(previous);
		final int quantityDelta = sameOffer ? current.quantity - previous.quantity : current.quantity;
		final long spentDelta = sameOffer ? (long) current.spent - previous.spent : current.spent;

		if (quantityDelta > 0)
		{
			record(offer.getItemId(), quantityDelta, Math.max(0, spentDelta));
		}
	}

	/** Replays the offers already in the slots, picking up anything bought while we were not running. */
	void syncOffers()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int slot = 0; slot < offers.length; slot++)
		{
			if (offers[slot] != null)
			{
				onOfferChanged(slot, offers[slot]);
			}
		}
	}

	/** Active periods, soonest to reset first. Safe to call off the client thread. */
	List<BuyLimitWindow> getActiveWindows()
	{
		final List<BuyLimitWindow> windows = new ArrayList<>();
		for (BuyLimitWindow window : readWindows())
		{
			if (!window.isExpired())
			{
				windows.add(window);
			}
		}
		windows.sort(Comparator.comparingLong(BuyLimitWindow::resetAt));
		return windows;
	}

	/**
	 * Drops periods whose four hours have elapsed and returns them - those limits are fully
	 * available again. Safe to call off the client thread.
	 */
	List<BuyLimitWindow> reapExpired()
	{
		final List<BuyLimitWindow> expired = new ArrayList<>();
		for (BuyLimitWindow window : readWindows())
		{
			if (window.isExpired())
			{
				configManager.unsetRSProfileConfiguration(LimitKeeperConfig.GROUP, WINDOW_PREFIX + window.itemId);
				expired.add(window);
			}
		}
		return expired;
	}

	/** The period for an item id, or null when nothing is tracked or it has elapsed. */
	BuyLimitWindow getWindow(int itemId)
	{
		final BuyLimitWindow window = readWindow(itemManager.canonicalize(itemId));
		return window == null || window.isExpired() ? null : window;
	}

	/** Forgets every tracked period for the logged in account. */
	void clear()
	{
		for (BuyLimitWindow window : readWindows())
		{
			configManager.unsetRSProfileConfiguration(LimitKeeperConfig.GROUP, WINDOW_PREFIX + window.itemId);
		}
	}

	private void record(int itemId, int quantity, long spent)
	{
		final int canonical = itemManager.canonicalize(itemId);
		final long now = System.currentTimeMillis();

		BuyLimitWindow window = readWindow(canonical);
		boolean wasAtLimit = window != null && !window.isExpired() && window.isAtLimit();

		if (window == null || window.isExpired())
		{
			window = new BuyLimitWindow();
			window.itemId = canonical;
			window.start = now;
			wasAtLimit = false;
		}

		if (!window.isLimitKnown())
		{
			final ItemStats stats = itemManager.getItemStats(canonical);
			window.limit = stats == null ? 0 : stats.getGeLimit();
		}

		if (window.name == null)
		{
			final ItemComposition composition = itemManager.getItemComposition(canonical);
			window.name = composition == null ? "Item " + canonical : composition.getName();
		}

		window.bought += quantity;
		window.spent += spent;
		configManager.setRSProfileConfiguration(LimitKeeperConfig.GROUP, WINDOW_PREFIX + canonical, gson.toJson(window));

		log.debug("Bought {} x {} ({}) for {}, {}/{} used this period", quantity, window.name,
			canonical, spent, window.bought, window.limit);

		if (!wasAtLimit && window.isAtLimit() && limitReachedListener != null)
		{
			limitReachedListener.accept(window);
		}
	}

	private List<BuyLimitWindow> readWindows()
	{
		final String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return List.of();
		}

		final List<BuyLimitWindow> windows = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(LimitKeeperConfig.GROUP, profile, WINDOW_PREFIX))
		{
			final BuyLimitWindow window = fromJson(configManager.getRSProfileConfiguration(LimitKeeperConfig.GROUP, key),
				BuyLimitWindow.class);
			if (window != null && window.itemId > 0)
			{
				windows.add(window);
			}
		}
		return windows;
	}

	private BuyLimitWindow readWindow(int itemId)
	{
		return fromJson(configManager.getRSProfileConfiguration(LimitKeeperConfig.GROUP, WINDOW_PREFIX + itemId),
			BuyLimitWindow.class);
	}

	private SlotOffer readSlot(int slot)
	{
		return fromJson(configManager.getRSProfileConfiguration(LimitKeeperConfig.GROUP, SLOT_PREFIX + slot),
			SlotOffer.class);
	}

	private <T> T fromJson(String json, Class<T> type)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}

		try
		{
			return gson.fromJson(json, type);
		}
		catch (JsonSyntaxException ex)
		{
			log.warn("Discarding malformed {} state", type.getSimpleName(), ex);
			return null;
		}
	}

	private static boolean isBuy(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
	}
}
