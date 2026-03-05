package com.banknotouch;

import com.google.inject.Provides;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Bank No Touch",
    description = "Tracks bank withdrawals and surfaces rarely-used items",
    tags = {"bank", "tracking", "inventory", "usage"}
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

    private final Map<Integer, Integer> bankSnapshot = new HashMap<>();
    private boolean bankOpen;

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
    }

    @Override
    protected void shutDown()
    {
        bankSnapshot.clear();
        bankOpen = false;
    }

    @Subscribe
    public void onGameTick(GameTick ignored)
    {
        boolean currentlyOpen = client.getWidget(ComponentID.BANK_ITEM_CONTAINER) != null;

        if (!bankOpen && currentlyOpen)
        {
            bankOpen = true;
            initializeSnapshotFromBank();
            return;
        }

        if (bankOpen && !currentlyOpen)
        {
            bankOpen = false;
            bankSnapshot.clear();
            if (config.reportOnClose())
            {
                publishLeastUsedReport();
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!bankOpen || event.getContainerId() != InventoryID.BANK.getId())
        {
            return;
        }

        ItemContainer bank = event.getItemContainer();
        if (bank == null)
        {
            return;
        }

        Map<Integer, Integer> current = toCountMap(bank);
        if (bankSnapshot.isEmpty())
        {
            bankSnapshot.putAll(current);
            return;
        }

        current.forEach((itemId, qty) -> {
            int previous = bankSnapshot.getOrDefault(itemId, 0);
            if (qty < previous)
            {
                store.recordWithdrawal(itemId, previous - qty);
            }
        });

        bankSnapshot.forEach((itemId, previous) -> {
            if (!current.containsKey(itemId) && previous > 0)
            {
                store.recordWithdrawal(itemId, previous);
            }
        });

        bankSnapshot.clear();
        bankSnapshot.putAll(current);
    }

    private void initializeSnapshotFromBank()
    {
        bankSnapshot.clear();
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (bank == null)
        {
            return;
        }

        bankSnapshot.putAll(toCountMap(bank));
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
        List<Map.Entry<Integer, WithdrawalStat>> leastUsed = store.loadAll().entrySet().stream()
            .filter(e -> e.getValue().getTimesWithdrawn() > 0)
            .sorted(LEAST_USED_COMPARATOR)
            .limit(config.reportItemCount())
            .collect(Collectors.toList());

        if (leastUsed.isEmpty())
        {
            sendMessage("Bank No Touch: No withdrawal data yet.");
            return;
        }

        String summary = leastUsed.stream()
            .map(e -> formatEntry(e.getKey(), e.getValue()))
            .collect(Collectors.joining(", "));

        sendMessage("Bank No Touch (least used): " + summary);
    }

    private String formatEntry(int itemId, WithdrawalStat stat)
    {
        String name = Text.removeTags(itemManager.getItemComposition(itemId).getName());
        return name + " [times=" + stat.getTimesWithdrawn() + ", qty=" + stat.getQuantityWithdrawn() + "]";
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
}
