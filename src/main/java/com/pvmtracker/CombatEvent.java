package com.pvmtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class CombatEvent
{
	private final int gameTick;

	@Getter
	public static class PlayerAttack extends CombatEvent
	{
		private final String weaponName;
		private final boolean isLostTick;
		private final int attackSpeed;

		public PlayerAttack(int gameTick, String weaponName, boolean isLostTick, int attackSpeed)
		{
			super(gameTick);
			this.weaponName = weaponName;
			this.isLostTick = isLostTick;
			this.attackSpeed = attackSpeed;
		}
	}

	@Getter
	public static class BossAttack extends CombatEvent
	{
		public enum AttackStyle
		{
			MELEE("Melee"),
			RANGE("Ranged"),
			MAGIC("Magic"),
			TYPELESS("Typeless");

			private final String name;

			AttackStyle(String name)
			{
				this.name = name;
			}

			public String getName()
			{
				return name;
			}
		}

		private final AttackStyle attackStyle;
		private final int damage;
		private final String playerPrayer;
		private final boolean isCorrectPrayer;

		public BossAttack(int gameTick, AttackStyle attackStyle, int damage, String playerPrayer, boolean isCorrectPrayer)
		{
			super(gameTick);
			this.attackStyle = attackStyle;
			this.damage = damage;
			this.playerPrayer = playerPrayer;
			this.isCorrectPrayer = isCorrectPrayer;
		}
	}
}
