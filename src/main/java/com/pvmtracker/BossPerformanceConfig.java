package com.pvmtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("pvmtracker")
public interface BossPerformanceConfig extends Config
{
	@ConfigItem(
		keyName = "trackMinCombatLevel",
		name = "Min NPC Combat Level",
		description = "Minimum combat level of NPCs to track if showOnlyBosses is disabled.",
		position = 1
	)
	default int trackMinCombatLevel()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "showOnlyBosses",
		name = "Track Bosses Only",
		description = "Only track known boss NPCs or NPCs with a boss health bar.",
		position = 2
	)
	default boolean showOnlyBosses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "historyLimit",
		name = "History Limit",
		description = "The maximum number of recent boss sessions to store in the sidebar history.",
		position = 3
	)
	default int historyLimit()
	{
		return 20;
	}
}
