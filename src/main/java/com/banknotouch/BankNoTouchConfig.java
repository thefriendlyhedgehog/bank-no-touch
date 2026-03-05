package com.banknotouch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(BankNoTouchConfig.GROUP)
public interface BankNoTouchConfig extends Config
{
    String GROUP = "banknotouch";

    @ConfigItem(
        keyName = "reportOnClose",
        name = "Show report when bank closes",
        description = "Print least-used withdrawn items when bank is closed"
    )
    default boolean reportOnClose()
    {
        return true;
    }

    @Range(min = 1, max = 50)
    @ConfigItem(
        keyName = "reportItemCount",
        name = "How many items to include",
        description = "Number of least-used items shown in the report"
    )
    default int reportItemCount()
    {
        return 10;
    }
}
