package com.thefriendlyhedgehog.banknotouch;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Bank No Touch",
    description = "Tracks bank withdrawals and surfaces rarely-used items with GE sell-off values",
    tags = {"bank", "tracking", "inventory", "usage", "gp", "sell"}
)
public class BankNoTouchPlugin extends Plugin
{
    private static final Comparator<Map.Entry<Integer, WithdrawalStat>> LEAST_USED_COMPARATOR =
        Comparator.<Map.Entry<Integer, WithdrawalStat>, Long>comparing(e -> e.getValue().getTimesWithdrawn())
            .thenComparing(e -> e.getValue().getQuantityWithdrawn())
            .thenComparing(e -> e.getValue().getLastWithdrawnEpochSecond());

    @Inject
    private Client client;

    @Inject
    private BankNoTouchConfig config;

    @Inject
    private BankNoTouchStore store;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private BankNoTouchPanel panel;

    private final Map<Integer, Integer> bankSnapshot = new HashMap<>();
    private boolean bankOpen;
    private NavigationButton navigationButton;

    @Provides
    BankNoTouchConfig getConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BankNoTouchConfig.class);
    }

    @Override
    protected void startUp()
    {
        bankSnapshot.clear();
        bankOpen = false;
        navigationButton = NavigationButton.builder()
            .tooltip("Bank No Touch")
            .icon(createSidebarIcon())
            .panel(panel)
            .priority(5)
            .build();
        clientToolbar.addNavigation(navigationButton);
        panel.refresh();
    }

    @Override
    protected void shutDown()
    {
        bankSnapshot.clear();
        bankOpen = false;
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick ignored)
    {
        boolean currentlyOpen = client.getWidget(ComponentID.BANK_ITEM_CONTAINER) != null;

        if (!bankOpen && currentlyOpen)
        {
            // Bank just opened — take the initial snapshot
            bankOpen = true;
            captureSnapshot();
            return;
        }

        if (bankOpen && currentlyOpen)
        {
            // Bank still open — detect withdrawals only (no persistence, no panel rebuild)
            detectWithdrawals();
            return;
        }

        if (bankOpen && !currentlyOpen)
        {
            // Bank just closed — persist snapshot, refresh panel once
            bankOpen = false;
            store.saveBankSnapshot(bankSnapshot);
            bankSnapshot.clear();
            if (config.reportOnClose())
            {
                publishLeastUsedReport();
            }
            panel.refresh();
        }
    }

    /**
     * Takes the initial snapshot when the bank opens.
     */
    private void captureSnapshot()
    {
        bankSnapshot.clear();
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (bank == null)
        {
            return;
        }
        bankSnapshot.putAll(toCountMap(bank));
    }

    /**
     * Diffs the current bank against the in-memory snapshot to record withdrawals.
     * Only updates the in-memory map — does NOT persist to config or refresh the panel.
     */
    private void detectWithdrawals()
    {
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (bank == null)
        {
            return;
        }

        Map<Integer, Integer> current = toCountMap(bank);

        // Detect quantity decreases (item withdrawn)
        current.forEach((itemId, qty) ->
        {
            int previous = bankSnapshot.getOrDefault(itemId, 0);
            if (qty < previous)
            {
                recordWithdrawalIfTracked(itemId, previous - qty);
            }
        });

        // Detect items that disappeared entirely (fully withdrawn)
        bankSnapshot.forEach((itemId, previous) ->
        {
            if (!current.containsKey(itemId) && previous > 0)
            {
                recordWithdrawalIfTracked(itemId, previous);
            }
        });

        // Replace the in-memory snapshot with current state
        bankSnapshot.clear();
        bankSnapshot.putAll(current);
    }

    private Map<Integer, Integer> toCountMap(ItemContainer container)
    {
        Map<Integer, Integer> map = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }
            map.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        return map;
    }

    private void publishLeastUsedReport()
    {
        Map<Integer, Integer> bankMemory = store.loadBankSnapshot();
        List<Map.Entry<Integer, WithdrawalStat>> leastUsed = bankMemory.keySet().stream()
            .filter(itemId -> !config.onlyEquipment() || isEquipment(itemManager.getItemComposition(itemId)))
            .map(itemId -> Map.entry(itemId, store.get(itemId)))
            .sorted(LEAST_USED_COMPARATOR)
            .limit(config.reportItemCount())
            .collect(Collectors.toList());

        if (leastUsed.isEmpty())
        {
            sendMessage("Bank No Touch: Open your bank once to cache items.");
            return;
        }

        sendMessage("Bank No Touch least used in current bank:");
        for (Map.Entry<Integer, WithdrawalStat> entry : leastUsed)
        {
            sendMessage(formatEntry(entry.getKey(), entry.getValue()));
        }
    }

    private String formatEntry(int itemId, WithdrawalStat stat)
    {
        String name = Text.removeTags(itemManager.getItemComposition(itemId).getName());
        return name + " - withdrawals " + stat.getTimesWithdrawn();
    }

    private void sendMessage(String message)
    {
        final String formatted = new ChatMessageBuilder()
            .append(message)
            .build();

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(formatted)
            .build());
    }

    private void recordWithdrawalIfTracked(int itemId, int quantity)
    {
        if (quantity <= 0)
        {
            return;
        }

        if (config.onlyEquipment() && !isEquipment(itemManager.getItemComposition(itemId)))
        {
            return;
        }

        store.recordWithdrawal(itemId, quantity);
    }

    private static boolean isEquipment(ItemComposition itemComposition)
    {
        String[] actions = itemComposition.getInventoryActions();
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

            String normalized = action.toLowerCase();
            if (normalized.equals("wear")
                || normalized.equals("wield")
                || normalized.equals("equip")
                || normalized.equals("hold"))
            {
                return true;
            }
        }

        return false;
    }

    private BufferedImage createSidebarIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(232, 232, 232));
        graphics.fillRoundRect(2, 2, 12, 12, 3, 3);
        graphics.setColor(ColorScheme.DARK_GRAY_COLOR);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawRoundRect(2, 2, 12, 12, 3, 3);

        graphics.setColor(new Color(255, 152, 31));
        graphics.fillRect(4, 5, 6, 2);
        graphics.fillRect(8, 5, 2, 6);
        graphics.fillPolygon(new int[]{8, 12, 8}, new int[]{11, 8, 5}, 3);
        graphics.dispose();
        return image;
    }
}
