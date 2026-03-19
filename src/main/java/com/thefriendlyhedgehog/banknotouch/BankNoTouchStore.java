package com.thefriendlyhedgehog.banknotouch;

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
    private static final String BANK_KEY_PREFIX = "bank_";

    private final ConfigManager configManager;
    private final Map<Integer, Integer> bankSnapshotCache = new HashMap<>();

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
            .map(this::toUsageEntry)
            .filter(e -> e != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    Map<Integer, Integer> loadBankSnapshot()
    {
        if (!bankSnapshotCache.isEmpty())
        {
            return new HashMap<>(bankSnapshotCache);
        }

        List<String> keys = configManager.getConfigurationKeys(BankNoTouchConfig.GROUP + ".");
        if (keys == null || keys.isEmpty())
        {
            return new HashMap<>();
        }

        Map<Integer, Integer> snapshot = keys.stream()
            .map(this::toBankEntry)
            .filter(e -> e != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        bankSnapshotCache.clear();
        bankSnapshotCache.putAll(snapshot);
        return new HashMap<>(bankSnapshotCache);
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

    void saveBankSnapshot(Map<Integer, Integer> snapshot)
    {
        bankSnapshotCache.clear();
        snapshot.forEach((itemId, quantity) ->
        {
            if (quantity > 0)
            {
                bankSnapshotCache.put(itemId, quantity);
            }
        });

        clearPersistedBankSnapshot();
        snapshot.forEach((itemId, quantity) ->
        {
            if (quantity > 0)
            {
                configManager.setConfiguration(BankNoTouchConfig.GROUP, bankKey(itemId), quantity);
            }
        });
    }

    private void set(int itemId, WithdrawalStat stat)
    {
        configManager.setConfiguration(BankNoTouchConfig.GROUP, itemKey(itemId), stat.serialize());
    }

    private Map.Entry<Integer, WithdrawalStat> toUsageEntry(String fullKey)
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

    private Map.Entry<Integer, Integer> toBankEntry(String fullKey)
    {
        String expectedPrefix = BankNoTouchConfig.GROUP + "." + BANK_KEY_PREFIX;
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

        String raw = configManager.getConfiguration(BankNoTouchConfig.GROUP, bankKey(itemId));
        if (raw == null)
        {
            return null;
        }

        try
        {
            int quantity = Integer.parseInt(raw);
            return quantity > 0 ? Map.entry(itemId, quantity) : null;
        }
        catch (NumberFormatException ignored)
        {
            return null;
        }
    }

    private void clearPersistedBankSnapshot()
    {
        List<String> keys = configManager.getConfigurationKeys(BankNoTouchConfig.GROUP + "." + BANK_KEY_PREFIX);
        if (keys == null || keys.isEmpty())
        {
            return;
        }

        for (String key : keys)
        {
            String configKey = key.substring((BankNoTouchConfig.GROUP + ".").length());
            configManager.unsetConfiguration(BankNoTouchConfig.GROUP, configKey);
        }
    }

    private static String itemKey(int itemId)
    {
        return ITEM_KEY_PREFIX + itemId;
    }

    private static String bankKey(int itemId)
    {
        return BANK_KEY_PREFIX + itemId;
    }
}
