package com.banknotouch;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
class BankNoTouchStore
{
    private static final String ITEM_KEY_PREFIX = "item_";

    private final ConfigManager configManager;

    @Inject
    private BankNoTouchStore(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    Map<Integer, WithdrawalStat> loadAll()
    {
        List<String> keys = configManager.getConfigurationKeys(BankNoTouchConfig.GROUP + ".");
        if (keys == null || keys.isEmpty())
        {
            return new HashMap<>();
        }

        return keys.stream()
            .map(this::toEntry)
            .filter(e -> e != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    WithdrawalStat recordWithdrawal(int itemId, int quantity)
    {
        WithdrawalStat current = get(itemId);
        WithdrawalStat updated = current.recordWithdrawal(quantity, Instant.now().getEpochSecond());
        set(itemId, updated);
        return updated;
    }

    WithdrawalStat get(int itemId)
    {
        String raw = configManager.getConfiguration(BankNoTouchConfig.GROUP, itemKey(itemId));
        return WithdrawalStat.deserialize(itemId, raw);
    }

    private void set(int itemId, WithdrawalStat stat)
    {
        configManager.setConfiguration(BankNoTouchConfig.GROUP, itemKey(itemId), stat.serialize());
    }

    private Map.Entry<Integer, WithdrawalStat> toEntry(String fullKey)
    {
        String expectedPrefix = BankNoTouchConfig.GROUP + "." + ITEM_KEY_PREFIX;
        if (!fullKey.startsWith(expectedPrefix))
        {
            return null;
        }

        String itemIdPart = fullKey.substring(expectedPrefix.length());
        int itemId;
        try
        {
            itemId = Integer.parseInt(itemIdPart);
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }

        return Map.entry(itemId, get(itemId));
    }

    private static String itemKey(int itemId)
    {
        return ITEM_KEY_PREFIX + itemId;
    }
}
