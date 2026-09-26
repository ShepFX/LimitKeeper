/*
 * Copyright (c) 2026, Corey Jenkins <https://github.com/ShepFX>
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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

@ConfigGroup(LimitKeeperConfig.GROUP)
public interface LimitKeeperConfig extends Config
{
	String GROUP = "limitkeeper";

	@ConfigItem(
		keyName = "showOnOfferScreen",
		name = "Show on offer screen",
		description = "Adds how much of the buy limit is left to the item text on the buy offer screen.",
		position = 1
	)
	default boolean showOnOfferScreen()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSidePanel",
		name = "Side panel",
		description = "Shows a side panel listing every item with a buy limit period running.",
		position = 2
	)
	default boolean showSidePanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInfoBoxes",
		name = "Infobox when maxed",
		description = "Shows an infobox counting down to the reset for each item whose buy limit you have used up.",
		position = 3
	)
	default boolean showInfoBoxes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUntrackedLimits",
		name = "Hide unknown limits",
		description = "Hides items that RuneLite has no buy limit for, instead of listing them with a count only.",
		position = 4
	)
	default boolean hideUntrackedLimits()
	{
		return false;
	}

	@ConfigItem(
		keyName = "limitReachedNotification",
		name = "Limit reached",
		description = "Notifies you when an item's buy limit is used up.",
		position = 5
	)
	default Notification limitReachedNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "limitResetNotification",
		name = "Limit reset",
		description = "Notifies you when a tracked item's four hour period elapses and the limit is available again.",
		position = 6
	)
	default Notification limitResetNotification()
	{
		return Notification.OFF;
	}
}
