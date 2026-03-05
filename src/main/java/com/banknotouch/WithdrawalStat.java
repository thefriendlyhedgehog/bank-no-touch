package com.banknotouch;

class WithdrawalStat
{
    private final int itemId;
    private final long timesWithdrawn;
    private final long quantityWithdrawn;
    private final long lastWithdrawnEpochSecond;

    WithdrawalStat(int itemId, long timesWithdrawn, long quantityWithdrawn, long lastWithdrawnEpochSecond)
    {
        this.itemId = itemId;
        this.timesWithdrawn = timesWithdrawn;
        this.quantityWithdrawn = quantityWithdrawn;
        this.lastWithdrawnEpochSecond = lastWithdrawnEpochSecond;
    }

    static WithdrawalStat empty(int itemId)
    {
        return new WithdrawalStat(itemId, 0L, 0L, 0L);
    }

    WithdrawalStat recordWithdrawal(int quantity, long epochSecond)
    {
        return new WithdrawalStat(
            itemId,
            timesWithdrawn + 1,
            quantityWithdrawn + quantity,
            Math.max(lastWithdrawnEpochSecond, epochSecond)
        );
    }

    String serialize()
    {
        return timesWithdrawn + "|" + quantityWithdrawn + "|" + lastWithdrawnEpochSecond;
    }

    static WithdrawalStat deserialize(int itemId, String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return empty(itemId);
        }

        String[] parts = raw.split("\\|");
        if (parts.length != 3)
        {
            return empty(itemId);
        }

        try
        {
            return new WithdrawalStat(
                itemId,
                Long.parseLong(parts[0]),
                Long.parseLong(parts[1]),
                Long.parseLong(parts[2])
            );
        }
        catch (NumberFormatException ignored)
        {
            return empty(itemId);
        }
    }

    int getItemId()
    {
        return itemId;
    }

    long getTimesWithdrawn()
    {
        return timesWithdrawn;
    }

    long getQuantityWithdrawn()
    {
        return quantityWithdrawn;
    }

    long getLastWithdrawnEpochSecond()
    {
        return lastWithdrawnEpochSecond;
    }
}
