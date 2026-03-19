package com.thefriendlyhedgehog.banknotouch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

@Singleton
class BankNoTouchPanel extends PluginPanel
{
	/** Gold tone matching the in-game GP colour. */
	private static final Color GP_COLOR = new Color(255, 200, 50);

	private final BankNoTouchStore store;
	private final BankNoTouchConfig config;
	private final ItemManager itemManager;

	private final JPanel listPanel = new JPanel();
	private final JLabel summaryLabel = new JLabel();
	private final JLabel itemCountLabel = createMetricValueLabel();
	private final JLabel potentialGpLabel = createMetricValueLabel();

	@Inject
	private BankNoTouchPanel(BankNoTouchStore store, BankNoTouchConfig config, ItemManager itemManager)
	{
		super(false);

		this.store = store;
		this.config = config;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(null);

		// ── Header ─────────────────────────────────────────────
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Bank No Touch");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setForeground(ColorScheme.TEXT_COLOR);
		title.setFont(FontManager.getRunescapeBoldFont());
		header.add(title);

		summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		summaryLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		summaryLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(summaryLabel);

		// Metric cards
		JPanel metricsPanel = new JPanel(new GridLayout(1, 2, 8, 0));
		metricsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		metricsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		metricsPanel.add(createMetricCard("Items to Sell", itemCountLabel));
		metricsPanel.add(createMetricCard("Potential GP", potentialGpLabel));
		metricsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		header.add(metricsPanel);
		header.add(createSpacer(8));

		JButton refreshButton = new JButton("Refresh");
		refreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		refreshButton.addActionListener(e -> refresh());
		SwingUtil.removeButtonDecorations(refreshButton);
		refreshButton.setForeground(ColorScheme.BRAND_ORANGE);
		refreshButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
		refreshButton.setBorder(BorderFactory.createEmptyBorder());
		refreshButton.setFocusPainted(false);
		header.add(refreshButton);

		add(header, BorderLayout.NORTH);

		// ── Scrollable item list ────────────────────────────────
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setBorder(null);
		scrollPane.setViewportBorder(null);
		add(scrollPane, BorderLayout.CENTER);
	}

	void refresh()
	{
		SwingUtilities.invokeLater(this::rebuildList);
	}

	// ── List building ───────────────────────────────────────────

	private void rebuildList()
	{
		SwingUtil.fastRemoveAll(listPanel);

		Map<Integer, Integer> bankSnapshot = store.loadBankSnapshot();

		List<BankItemView> sellItems = bankSnapshot.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.map(e -> toBankItemView(e.getKey(), e.getValue()))
			.filter(this::matchesEquipmentFilter)
			.filter(this::matchesRarelyUsed)
			.sorted(BankItemView.COMPARATOR)
			.collect(Collectors.toList());

		long grandTotalGp = sellItems.stream()
			.mapToLong(BankItemView::getTotalGpValue)
			.sum();

		// Update header metrics
		boolean hasData = !bankSnapshot.isEmpty();
		summaryLabel.setText(hasData
			? "Items rarely withdrawn \u2014 sell for:"
			: "Open your bank once to refresh memory");
		itemCountLabel.setText(String.valueOf(sellItems.size()));
		potentialGpLabel.setText(formatGp(grandTotalGp));
		potentialGpLabel.setForeground(grandTotalGp > 0 ? GP_COLOR : ColorScheme.TEXT_COLOR);

		// Populate rows
		if (!hasData)
		{
			listPanel.add(buildEmptyLabel("Open your bank to cache its contents, then review rarely-used items here."));
		}
		else if (sellItems.isEmpty())
		{
			listPanel.add(buildEmptyLabel(
				"No items match your current threshold (" + config.rarelyUsedThreshold() + " withdrawals). "
					+ "Lower the threshold in plugin settings to see more."));
		}
		else
		{
			for (BankItemView item : sellItems)
			{
				listPanel.add(createItemRow(item));
				listPanel.add(createSpacer(4));
			}
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	// ── Row construction ────────────────────────────────────────

	private JPanel createItemRow(BankItemView item)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)
		));
		// Let the row size itself naturally rather than a fixed max height,
		// so text never gets clipped at any DPI.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		// Item icon
		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(36, 36));
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage image = itemManager.getImage(item.getItemId(), 1, false);
		image.addTo(iconLabel);
		row.add(iconLabel, BorderLayout.WEST);

		// Name + usage stats
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setOpaque(false);

		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setForeground(ColorScheme.TEXT_COLOR);
		nameLabel.setFont(FontManager.getDefaultBoldFont());
		textPanel.add(nameLabel);

		String withdrawalText = item.getStat().getTimesWithdrawn() == 0
			? "Never withdrawn"
			: item.getStat().getTimesWithdrawn() + "\u00d7 withdrawn";
		JLabel statsLabel = new JLabel(withdrawalText + "  \u00b7  " + item.getBankQuantity() + " in bank");
		statsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		textPanel.add(statsLabel);

		// Unit price line (show "Untradeable" for items with no GE value)
		String priceText = item.getUnitGpValue() > 0
			? formatGp(item.getUnitGpValue()) + " ea"
			: "Untradeable";
		JLabel priceLabel = new JLabel(priceText);
		priceLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		textPanel.add(priceLabel);

		row.add(textPanel, BorderLayout.CENTER);

		// GP value pill (total stack value)
		String gpText = item.getTotalGpValue() > 0
			? formatGp(item.getTotalGpValue())
			: "N/A";
		JLabel gpPill = new JLabel(gpText);
		gpPill.setHorizontalAlignment(SwingConstants.CENTER);
		gpPill.setForeground(item.getTotalGpValue() > 0 ? GP_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		gpPill.setFont(FontManager.getRunescapeBoldFont());
		stylePill(gpPill);
		row.add(gpPill, BorderLayout.EAST);

		return row;
	}

	// ── Helpers ─────────────────────────────────────────────────

	private BankItemView toBankItemView(int itemId, int quantity)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		WithdrawalStat stat = store.get(itemId);
		long unitPrice = itemManager.getItemPrice(itemId);
		long totalGp = unitPrice * quantity;
		return new BankItemView(
			itemId,
			quantity,
			Text.removeTags(comp.getName()),
			comp,
			stat,
			unitPrice,
			totalGp
		);
	}

	private boolean matchesEquipmentFilter(BankItemView item)
	{
		return !config.onlyEquipment() || isEquipment(item.getItemComposition());
	}

	private boolean matchesRarelyUsed(BankItemView item)
	{
		return item.getStat().getTimesWithdrawn() < config.rarelyUsedThreshold();
	}

	private static boolean isEquipment(ItemComposition comp)
	{
		String[] actions = comp.getInventoryActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action == null)
			{
				continue;
			}
			String a = action.toLowerCase();
			if (a.equals("wear") || a.equals("wield") || a.equals("equip") || a.equals("hold"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Formats a GP value with K / M / B suffix.
	 * e.g. 1_250_000 -> "1.2M",  45_000 -> "45.0K",  800 -> "800"
	 */
	static String formatGp(long gp)
	{
		if (gp >= 1_000_000_000)
		{
			return String.format("%.1fB", gp / 1_000_000_000.0);
		}
		if (gp >= 1_000_000)
		{
			return String.format("%.1fM", gp / 1_000_000.0);
		}
		if (gp >= 1_000)
		{
			return String.format("%.1fK", gp / 1_000.0);
		}
		return String.valueOf(gp);
	}

	private JPanel createMetricCard(String title, JLabel valueLabel)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(titleLabel);

		valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(valueLabel);
		return card;
	}

	private static JLabel createMetricValueLabel()
	{
		JLabel label = new JLabel("-");
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}

	private static JLabel buildEmptyLabel(String text)
	{
		JLabel label = new JLabel("<html><body style='width:185px'>" + text + "</body></html>");
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(14, 12, 14, 12)
		));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setOpaque(true);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static Component createSpacer(int height)
	{
		return javax.swing.Box.createRigidArea(new Dimension(0, height));
	}

	private static void stylePill(JComponent component)
	{
		component.setOpaque(true);
		component.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		component.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
	}

	// ── View model ──────────────────────────────────────────────

	static final class BankItemView
	{
		/**
		 * Sort: highest total GP value first, then never-withdrawn before withdrawn,
		 * then alphabetical by name as a tiebreaker.
		 */
		static final Comparator<BankItemView> COMPARATOR =
			Comparator.comparingLong(BankItemView::getTotalGpValue).reversed()
				.thenComparing(item -> item.getStat().getTimesWithdrawn())
				.thenComparing(BankItemView::getName, String.CASE_INSENSITIVE_ORDER);

		private final int itemId;
		private final int bankQuantity;
		private final String name;
		private final ItemComposition itemComposition;
		private final WithdrawalStat stat;
		private final long unitGpValue;
		private final long totalGpValue;

		BankItemView(int itemId, int bankQuantity, String name,
			ItemComposition itemComposition, WithdrawalStat stat,
			long unitGpValue, long totalGpValue)
		{
			this.itemId = itemId;
			this.bankQuantity = bankQuantity;
			this.name = name;
			this.itemComposition = itemComposition;
			this.stat = stat;
			this.unitGpValue = unitGpValue;
			this.totalGpValue = totalGpValue;
		}

		int getItemId() { return itemId; }
		int getBankQuantity() { return bankQuantity; }
		String getName() { return name; }
		ItemComposition getItemComposition() { return itemComposition; }
		WithdrawalStat getStat() { return stat; }
		long getUnitGpValue() { return unitGpValue; }
		long getTotalGpValue() { return totalGpValue; }
	}
}
