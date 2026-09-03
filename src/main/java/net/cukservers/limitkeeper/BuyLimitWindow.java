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
	/** The item's buy limit, or 0 when RuneLite has no limit for it. */
	int limit;
	/** Epoch millis of the first buy tracked in this period. */
	long start;
	/** Item name, captured on the client thread so the side panel never has to look it up. */
	String name;

	long resetAt()
	{
		return start + BuyLimitTracker.WINDOW_MS;
	}

	long remainingMillis()
	{
		return resetAt() - System.currentTimeMillis();
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
