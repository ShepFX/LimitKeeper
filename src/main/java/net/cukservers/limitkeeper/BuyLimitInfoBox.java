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

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * Counts down to the reset of one item whose buy limit is used up.
 *
 * <p>RuneLite's own {@link net.runelite.client.ui.overlay.infobox.Timer} cannot be used here. Its
 * text takes the remaining seconds modulo an hour, so it drops whole hours: a period with 2:47:03
 * left renders as "47:03", falls to zero and starts again at 59:59, twice, before the limit
 * actually frees up. Buy limit periods run for four hours and so spend three of them inside the
 * range that gets truncated, which is exactly when someone is looking to see how long is left.
 */
class BuyLimitInfoBox extends InfoBox
{
	private final BuyLimitWindow window;

	BuyLimitInfoBox(BufferedImage image, Plugin plugin, BuyLimitWindow window)
	{
		super(image, plugin);
		this.window = window;
	}

	@Override
	public String getText()
	{
		return window.remainingText();
	}

	@Override
	public Color getTextColor()
	{
		return Color.WHITE;
	}

	/**
	 * The plugin's refresh loop removes this box when the period ends or is cleared, but culling on
	 * expiry too means a stale box cannot outlive its window if that loop ever stops running.
	 */
	@Override
	public boolean cull()
	{
		return window.isExpired();
	}
}
