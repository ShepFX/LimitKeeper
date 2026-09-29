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

/**
 * Snapshot of one Grand Exchange slot, persisted so quantities bought between sessions are counted
 * once instead of being replayed as new purchases at every login.
 */
class SlotOffer
{
	int itemId;
	/** Price per item. Long because the client widened it in 1.13.0, for prices above 2^31-1. */
	long price;
	int total;
	int quantity;
	/** Coins spent so far on this offer. Widened alongside {@link #price}. */
	long spent;

	SlotOffer()
	{
	}

	SlotOffer(int itemId, long price, int total, int quantity, long spent)
	{
		this.itemId = itemId;
		this.price = price;
		this.total = total;
		this.quantity = quantity;
		this.spent = spent;
	}

	/**
	 * Whether {@code other} is a snapshot of the same offer. Offers cannot be edited in place, so an
	 * item, price and total that all match means the slot still holds the offer we last saw.
	 */
	boolean isSameOffer(SlotOffer other)
	{
		return other != null && other.itemId == itemId && other.price == price && other.total == total;
	}
}
