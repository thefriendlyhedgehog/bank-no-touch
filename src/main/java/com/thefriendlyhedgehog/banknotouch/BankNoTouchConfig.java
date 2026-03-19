package com.thefriendlyhedgehog.banknotouch;

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
		name = "Show chat report when bank closes",
		description = "Print least-used withdrawn items to chat when the bank is closed"
	)
	default boolean reportOnClose()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyEquipment",
		name = "Only track equipment",
		description = "Only record withdrawals for wearable or wieldable items"
	)
	default boolean onlyEquipment()
	{
		return false;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "reportItemCount",
		name = "Chat report item count",
		description = "Number of least-used items shown in the chat report"
	)
	default int reportItemCount()
	{
		return 10;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "rarelyUsedThreshold",
		name = "Rarely-used threshold (withdrawals)",
		description = "Items withdrawn fewer than this many times are shown in the sell-off panel. "
			+ "1 = only items never withdrawn. 3 = withdrawn 0\u20132 times (default)."
	)
	default int rarelyUsedThreshold()
	{
		return 3;
	}
}
