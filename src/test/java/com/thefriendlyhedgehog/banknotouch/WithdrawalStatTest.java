package com.thefriendlyhedgehog.banknotouch;

import org.junit.Assert;
import org.junit.Test;

public class WithdrawalStatTest
{
    @Test
    public void recordWithdrawalIncrementsTimesAndQuantity()
    {
        WithdrawalStat stat = WithdrawalStat.empty(4151);

        WithdrawalStat updated = stat.recordWithdrawal(7, 1700000000L);

        Assert.assertEquals(4151, updated.getItemId());
        Assert.assertEquals(1L, updated.getTimesWithdrawn());
        Assert.assertEquals(7L, updated.getQuantityWithdrawn());
        Assert.assertEquals(1700000000L, updated.getLastWithdrawnEpochSecond());
    }

    @Test
    public void recordWithdrawalKeepsLatestTimestamp()
    {
        WithdrawalStat stat = new WithdrawalStat(4151, 4L, 20L, 1700000100L);

        WithdrawalStat updated = stat.recordWithdrawal(3, 1700000000L);

        Assert.assertEquals(5L, updated.getTimesWithdrawn());
        Assert.assertEquals(23L, updated.getQuantityWithdrawn());
        Assert.assertEquals(1700000100L, updated.getLastWithdrawnEpochSecond());
    }

    @Test
    public void serializationRoundTripWorks()
    {
        WithdrawalStat stat = new WithdrawalStat(995, 12L, 4500L, 1700000999L);

        String serialized = stat.serialize();
        WithdrawalStat decoded = WithdrawalStat.deserialize(995, serialized);

        Assert.assertEquals(995, decoded.getItemId());
        Assert.assertEquals(12L, decoded.getTimesWithdrawn());
        Assert.assertEquals(4500L, decoded.getQuantityWithdrawn());
        Assert.assertEquals(1700000999L, decoded.getLastWithdrawnEpochSecond());
    }

    @Test
    public void invalidSerializedInputFallsBackToEmpty()
    {
        WithdrawalStat decoded = WithdrawalStat.deserialize(1513, "bad|value");

        Assert.assertEquals(1513, decoded.getItemId());
        Assert.assertEquals(0L, decoded.getTimesWithdrawn());
        Assert.assertEquals(0L, decoded.getQuantityWithdrawn());
        Assert.assertEquals(0L, decoded.getLastWithdrawnEpochSecond());
    }
}
