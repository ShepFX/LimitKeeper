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

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@PluginDescriptor(
	name = "Limit Keeper",
	description = "Tracks how much of each item's 4 hour Grand Exchange buy limit you have used",
	tags = {"ge", "grand", "exchange", "buy", "limit", "trade", "flip", "merch"}
)
public class LimitKeeperPlugin extends Plugin
{
	/** Present in the offer text once we have added our line, so we only add it once. */
	private static final String MARKER = "Bought:";
	private static final long REFRESH_PERIOD_MS = 1000;
	/** How much of the item examine text to match on when locating the widget it was written to. */
	private static final int NEEDLE_LENGTH = 20;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private LimitKeeperConfig config;

	@Inject
	private BuyLimitTracker tracker;

	private LimitKeeperPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshTask;

	/** Item whose buy offer is being set up, or -1 when no buy offer screen is open. */
	private int examineItemId = -1;
	private String examineText;

	@Provides
	LimitKeeperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LimitKeeperConfig.class);
	}

	@Override
	protected void startUp()
	{
		tracker.setLimitReachedListener(this::onLimitReached);

		if (config.showSidePanel())
		{
			addPanel();
		}

		refreshTask = executor.scheduleAtFixedRate(this::refresh, 0, REFRESH_PERIOD_MS, TimeUnit.MILLISECONDS);
		clientThread.invoke(tracker::syncOffers);
	}

	@Override
	protected void shutDown()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
			refreshTask = null;
		}

		tracker.setLimitReachedListener(null);
		removePanel();
		examineItemId = -1;
		examineText = null;
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		tracker.onOfferChanged(event.getSlot(), event.getOffer());
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		switch (event.getEventName())
		{
			case "geBuyExamineText":
			{
				final Object[] stack = client.getObjectStack();
				final int size = client.getObjectStackSize();
				examineItemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
				examineText = size >= 3 && stack[size - 3] instanceof String ? (String) stack[size - 3] : null;
				break;
			}
			case "geSellExamineText":
				// Sell offers have no buy limit, and the sell screen reuses the same widgets.
				examineItemId = -1;
				examineText = null;
				break;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (examineItemId <= 0 || !config.showOnOfferScreen())
		{
			return;
		}

		final Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			examineItemId = -1;
			examineText = null;
			return;
		}

		final BuyLimitWindow window = tracker.getWindow(examineItemId);
		if (window == null)
		{
			return;
		}

		final Widget target = findOfferTextWidget(setup);
		if (target == null)
		{
			return;
		}

		final String text = target.getText();
		if (text == null || text.contains(MARKER))
		{
			return;
		}

		target.setText(text + "<br>" + describe(window));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LimitKeeperConfig.GROUP.equals(event.getGroup()) || !"showSidePanel".equals(event.getKey()))
		{
			return;
		}

		if (config.showSidePanel())
		{
			addPanel();
		}
		else
		{
			removePanel();
		}
	}

	/**
	 * Locates the widget holding the offer's item text. The description component is the normal
	 * home for it, but the offer screen has been rearranged before, so fall back to whichever child
	 * the game wrote the examine text into.
	 */
	private Widget findOfferTextWidget(Widget setup)
	{
		final Widget description = client.getWidget(InterfaceID.GeOffers.SETUP_DESC);
		if (description != null && !description.isHidden() && description.getText() != null
			&& !description.getText().isEmpty())
		{
			return description;
		}

		if (examineText == null || examineText.isEmpty())
		{
			return null;
		}

		final String needle = examineText.substring(0, Math.min(NEEDLE_LENGTH, examineText.length()));
		return findChildContaining(setup, needle);
	}

	private static Widget findChildContaining(Widget parent, String needle)
	{
		final Widget[] children = parent.getChildren();
		if (children == null)
		{
			return null;
		}

		for (Widget child : children)
		{
			if (child == null || child.isHidden())
			{
				continue;
			}

			final String text = child.getText();
			if (text != null && text.contains(needle))
			{
				return child;
			}
		}

		return null;
	}

	private void refresh()
	{
		try
		{
			for (BuyLimitWindow expired : tracker.reapExpired())
			{
				if (expired.isAtLimit())
				{
					notifier.notify(config.limitResetNotification(),
						"Your buy limit on " + expired.name + " has reset.");
				}
			}

			if (panel != null)
			{
				final List<BuyLimitWindow> windows = visibleWindows();
				SwingUtilities.invokeLater(() -> panel.update(windows));
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to refresh buy limits", ex);
		}
	}

	private List<BuyLimitWindow> visibleWindows()
	{
		final List<BuyLimitWindow> windows = tracker.getActiveWindows();
		if (!config.hideUntrackedLimits())
		{
			return windows;
		}

		windows.removeIf(window -> !window.isLimitKnown());
		return windows;
	}

	private void onLimitReached(BuyLimitWindow window)
	{
		notifier.notify(config.limitReachedNotification(), "You have bought your "
			+ QuantityFormatter.formatNumber(window.limit) + " limit of " + window.name + ".");
	}

	private void addPanel()
	{
		if (navButton != null)
		{
			return;
		}

		panel = new LimitKeeperPanel(itemManager, this::clearTracking);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Buy limits")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	private void removePanel()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		panel = null;
	}

	private void clearTracking()
	{
		tracker.clear();

		if (panel != null)
		{
			panel.update(List.of());
		}
	}

	private static String describe(BuyLimitWindow window)
	{
		final StringBuilder text = new StringBuilder("<col=32a0fa>")
			.append(MARKER)
			.append(' ')
			.append(QuantityFormatter.formatNumber(window.bought));

		if (window.isLimitKnown())
		{
			text.append(" / ").append(QuantityFormatter.formatNumber(window.limit)).append("</col> ");
			text.append(window.isAtLimit() ? "<col=e61e1e>limit reached" : "<col=37f046>"
				+ QuantityFormatter.formatNumber(window.remaining()) + " left");
		}

		text.append("</col> <col=a5a5a5>resets in ")
			.append(LimitKeeperPanel.formatRemaining(window.remainingMillis()))
			.append("</col>");

		if (window.spent > 0)
		{
			text.append("<br><col=6ee16e>")
				.append(QuantityFormatter.quantityToStackSize(window.spent))
				.append(" gp spent</col> <col=a5a5a5>(")
				.append(QuantityFormatter.quantityToStackSize(window.averagePrice()))
				.append(" ea)</col>");
		}

		return text.toString();
	}
}
