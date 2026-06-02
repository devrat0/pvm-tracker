package com.pvmtracker;

import net.runelite.api.ItemID;

public class WeaponSpeedLookup
{
	public static int getSpeed(int itemId, String name)
	{
		// 1. Precise Item ID matches
		switch (itemId)
		{
			case ItemID.TOXIC_BLOWPIPE:
			case ItemID.TOXIC_BLOWPIPE_EMPTY:
				return 2; // blowpipe on rapid in PvM

			case ItemID.SCYTHE_OF_VITUR:
			case ItemID.SCYTHE_OF_VITUR_UNCHARGED:
			case ItemID.TWISTED_BOW:
			case ItemID.OSMUMTENS_FANG:
				return 5;

			case ItemID.ELDER_MAUL:
			case ItemID.ELDER_MAUL_OR:
			case ItemID.ARMADYL_GODSWORD:
			case ItemID.ARMADYL_GODSWORD_OR:
			case ItemID.BANDOS_GODSWORD:
			case ItemID.BANDOS_GODSWORD_OR:
			case ItemID.SARADOMIN_GODSWORD:
			case ItemID.SARADOMIN_GODSWORD_OR:
			case ItemID.ZAMORAK_GODSWORD:
			case ItemID.ZAMORAK_GODSWORD_OR:
			case ItemID.ANCIENT_GODSWORD:
				return 6;
		}

		// 2. Keyword matches (highly reliable heuristics for all other weapons)
		if (name == null)
		{
			return 4;
		}

		String lower = name.toLowerCase();

		if (lower.contains("blowpipe"))
		{
			return 2;
		}

		if (lower.contains("dart") || lower.contains("knife") || lower.contains("shortbow") || lower.contains("throwing axe"))
		{
			return 3;
		}

		if (lower.contains("godsword") || lower.contains("maul") || lower.contains("2h sword") || lower.contains("colossus"))
		{
			return 6;
		}

		if (lower.contains("scythe") || lower.contains("fang") || lower.contains("shadow") || lower.contains("crossbow") || lower.contains("ballista") || lower.contains("halberd") || lower.contains("soulreaper"))
		{
			return 5;
		}

		if (lower.contains("whip") || lower.contains("tentacle") || lower.contains("rapier") || lower.contains("mace") || lower.contains("trident") || lower.contains("staff") || lower.contains("wand") || lower.contains("scimitar") || lower.contains("dagger") || lower.contains("claw") || lower.contains("faerdhinen") || lower.contains("crystal bow") || lower.contains("saeldor"))
		{
			return 4;
		}

		return 4; // Standard fallback
	}
}
