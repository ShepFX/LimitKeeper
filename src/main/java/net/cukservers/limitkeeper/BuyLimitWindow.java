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

/**
 * One item's 4 hour buy limit period. Serialised to the RS profile config as JSON, so this is a
 * plain data holder with stable field names - renaming a field discards users' tracked history.
 */
class BuyLimitWindow
{
	/** Canonical (unnoted, non-placeholder) item id. */
	int itemId;
	/** Quantity bought since {@link #start}. */
	int bought;
	/** Coins spent on those buys, so the average paid can be shown next to the count. */
	long spent;
	/** The item's buy limit, or 0 when RuneLite has no limit for it. */
	int limit;
	/** Epoch millis of the first buy tracked in this period. */
	long start;
	/** Item name, captured on the client thread so the side panel never has to look it up. */
	String name;

	/** Mean price paid per item this period, or 0 before anything has been bought. */
	long averagePrice()
	{
		return bought > 0 ? spent / bought : 0;
	}

	long resetAt()
	{
		return start + BuyLimitTracker.WINDOW_MS;
	}

	long remainingMillis()
	{
		return resetAt() - System.currentTimeMillis();
	}

	/**
	 * Time until this period resets, as {@code h:mm:ss} or {@code m:ss} once under an hour.
	 *
	 * <p>Deliberately not RuneLite's {@link net.runelite.client.ui.overlay.infobox.Timer}, whose text
	 * takes the remaining seconds modulo an hour and so drops whole hours: with 2:47:03 left it
	 * reads "47:03", counting down to zero and rolling over twice before the limit actually frees
	 * up. A four hour period spends most of its life in the range that gets truncated.
	 */
	String remainingText()
	{
		final long seconds = Math.max(0, remainingMillis()) / 1000;
		final long hours = seconds / 3600;
		final long minutes = (seconds % 3600) / 60;

		if (hours > 0)
		{
			return String.format("%d:%02d:%02d", hours, minutes, seconds % 60);
		}

		return String.format("%d:%02d", minutes, seconds % 60);
	}

	boolean isExpired()
	{
		return remainingMillis() <= 0;
	}

	boolean isLimitKnown()
	{
		return limit > 0;
	}

	/** Quantity still buyable in this period, or -1 when the limit is unknown. */
	int remaining()
	{
		return isLimitKnown() ? Math.max(0, limit - bought) : -1;
	}

	boolean isAtLimit()
	{
		return isLimitKnown() && bought >= limit;
	}
}
