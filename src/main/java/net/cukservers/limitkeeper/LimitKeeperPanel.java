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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.ThinProgressBar;
import net.runelite.client.util.QuantityFormatter;

/** Lists the items with a buy limit period running, soonest to reset first. */
class LimitKeeperPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final Runnable clearAction;

	private final JPanel list = new JPanel();
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();
	private final Map<Integer, JLabel> timerLabels = new HashMap<>();

	private String rowSignature = null;

	LimitKeeperPanel(ItemManager itemManager, Runnable clearAction)
	{
		super(false);
		this.itemManager = itemManager;
		this.clearAction = clearAction;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		list.setLayout(new DynamicGridLayout(0, 1, 0, 4));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listWrapper.add(list, BorderLayout.NORTH);

		final JScrollPane scrollPane = new JScrollPane(listWrapper);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));

		errorPanel.setContent("Buy limits",
			"Nothing is being tracked yet. Buy an item on the Grand Exchange and its four hour "
				+ "period will appear here.");

		add(createHeader(), BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
	}

	private JPanel createHeader()
	{
		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 6, 0));

		final JLabel title = new JLabel("Active buy limits");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);

		final JButton clear = new JButton("Clear");
		clear.setFont(FontManager.getRunescapeSmallFont());
		clear.setToolTipText("Forget every tracked buy limit for this account");
		clear.setFocusable(false);
		clear.addActionListener(e ->
		{
			final int confirm = JOptionPane.showConfirmDialog(this,
				"Forget every tracked buy limit for this account?", "Clear buy limits",
				JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION)
			{
				clearAction.run();
			}
		});

		header.add(title, BorderLayout.WEST);
		header.add(clear, BorderLayout.EAST);
		return header;
	}

	/** Redraws the list. Must be called on the Swing thread. */
	void update(List<BuyLimitWindow> windows)
	{
		final String signature = signature(windows);
		if (signature.equals(rowSignature))
		{
			// Only the countdowns moved, so leave the rows alone and retick the labels.
			updateTimers(windows);
			return;
		}

		rowSignature = signature;
		timerLabels.clear();
		list.removeAll();

		if (windows.isEmpty())
		{
			list.add(errorPanel);
		}
		else
		{
			for (BuyLimitWindow window : windows)
			{
				list.add(createRow(window));
			}
		}

		list.revalidate();
		list.repaint();
	}

	private void updateTimers(List<BuyLimitWindow> windows)
	{
		for (BuyLimitWindow window : windows)
		{
			final JLabel label = timerLabels.get(window.itemId);
			if (label != null)
			{
				label.setText(window.remainingText());
			}
		}
	}

	private JPanel createRow(BuyLimitWindow window)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 4, 4, 4));

		final JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(window.itemId).addTo(icon);

		final JLabel name = new JLabel(window.name == null ? "Item " + window.itemId : window.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.TEXT_COLOR);

		final JLabel counts = new JLabel(describeCounts(window));
		counts.setFont(FontManager.getRunescapeSmallFont());
		counts.setForeground(window.isAtLimit() ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);

		final boolean showSpend = window.spent > 0;

		final JPanel text = new JPanel(new DynamicGridLayout(showSpend ? 3 : 2, 1, 0, 1));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.add(name);
		text.add(counts);

		if (showSpend)
		{
			final JLabel spend = new JLabel(describeSpend(window));
			spend.setFont(FontManager.getRunescapeSmallFont());
			spend.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			spend.setToolTipText("Coins spent this period, and the average paid per item");
			text.add(spend);
		}

		final JLabel timer = new JLabel(window.remainingText());
		timer.setFont(FontManager.getRunescapeSmallFont());
		timer.setForeground(ColorScheme.GRAND_EXCHANGE_LIMIT);
		timer.setToolTipText("Time until this item's buy limit resets");
		timerLabels.put(window.itemId, timer);

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(timer, BorderLayout.EAST);

		if (window.isLimitKnown())
		{
			final ThinProgressBar progress = new ThinProgressBar();
			progress.setMaximumValue(window.limit);
			progress.setValue(Math.min(window.bought, window.limit));
			progress.setForeground(window.isAtLimit()
				? ColorScheme.PROGRESS_ERROR_COLOR
				: ColorScheme.PROGRESS_INPROGRESS_COLOR);
			row.add(progress, BorderLayout.SOUTH);
		}

		return row;
	}

	private static String describeSpend(BuyLimitWindow window)
	{
		return QuantityFormatter.quantityToStackSize(window.spent) + " gp - "
			+ QuantityFormatter.quantityToStackSize(window.averagePrice()) + " ea";
	}

	private static String describeCounts(BuyLimitWindow window)
	{
		if (!window.isLimitKnown())
		{
			return QuantityFormatter.formatNumber(window.bought) + " bought (limit unknown)";
		}

		return QuantityFormatter.formatNumber(window.bought) + " / "
			+ QuantityFormatter.formatNumber(window.limit) + " - "
			+ QuantityFormatter.formatNumber(window.remaining()) + " left";
	}

	private static String signature(List<BuyLimitWindow> windows)
	{
		final List<String> parts = new ArrayList<>(windows.size());
		for (BuyLimitWindow window : windows)
		{
			parts.add(window.itemId + ":" + window.bought + ":" + window.limit + ":" + window.spent);
		}
		return String.join(",", parts);
	}
}
