package com.pvmtracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class CombatSession
{
	private final String bossName;
	private final int startTick;
	private final Instant startTime;

	@Setter
	private int endTick;
	
	private final List<CombatEvent> events = new ArrayList<>();

	private int totalLostTicks = 0;
	private int actualAttacks = 0;
	private int missedPrayers = 0;
	private int avoidableDamage = 0;

	@Setter
	private int activeWeaponSpeed = 4; // Default to 4-tick weapon speed if unknown

	public CombatSession(String bossName, int startTick)
	{
		this.bossName = bossName;
		this.startTick = startTick;
		this.startTime = Instant.now();
	}

	public void addEvent(CombatEvent event)
	{
		events.add(event);

		if (event instanceof CombatEvent.PlayerAttack)
		{
			CombatEvent.PlayerAttack playerAttack = (CombatEvent.PlayerAttack) event;
			if (playerAttack.isLostTick())
			{
				totalLostTicks++;
			}
			else
			{
				actualAttacks++;
				if (playerAttack.getAttackSpeed() > 0)
				{
					activeWeaponSpeed = playerAttack.getAttackSpeed();
				}
			}
		}
		else if (event instanceof CombatEvent.BossAttack)
		{
			CombatEvent.BossAttack bossAttack = (CombatEvent.BossAttack) event;
			if (!bossAttack.isCorrectPrayer())
			{
				missedPrayers++;
				if (bossAttack.getDamage() > 0)
				{
					avoidableDamage += bossAttack.getDamage();
				}
			}
		}
	}

	public int getDurationTicks()
	{
		return endTick > startTick ? (endTick - startTick) : 0;
	}

	public int getDurationTicksActive(int currentTick)
	{
		int end = endTick > startTick ? endTick : currentTick;
		return Math.max(0, end - startTick);
	}

	public double getUptimePercent()
	{
		int duration = getDurationTicks();
		if (duration <= 0)
		{
			return 100.0;
		}
		
		double activeTicks = Math.max(0, duration - totalLostTicks);
		return (activeTicks / duration) * 100.0;
	}

	public double getUptimePercentActive(int currentTick)
	{
		int duration = getDurationTicksActive(currentTick);
		if (duration <= 0)
		{
			return 100.0;
		}
		double activeTicks = Math.max(0, duration - totalLostTicks);
		return (activeTicks / duration) * 100.0;
	}

	public double getPrayerAccuracy()
	{
		int totalAttacks = 0;
		int correct = 0;
		for (CombatEvent event : events)
		{
			if (event instanceof CombatEvent.BossAttack)
			{
				CombatEvent.BossAttack ba = (CombatEvent.BossAttack) event;
				if (ba.getAttackStyle() != CombatEvent.BossAttack.AttackStyle.TYPELESS)
				{
					totalAttacks++;
					if (ba.isCorrectPrayer())
					{
						correct++;
					}
				}
			}
		}
		return totalAttacks > 0 ? ((double) correct / totalAttacks) * 100.0 : 100.0;
	}

	public String getFormattedDuration()
	{
		int seconds = (int) (getDurationTicks() * 0.6);
		int mins = seconds / 60;
		int secs = seconds % 60;
		return String.format("%d:%02d", mins, secs);
	}

	public String getFormattedDurationActive(int currentTick)
	{
		int seconds = (int) (getDurationTicksActive(currentTick) * 0.6);
		int mins = seconds / 60;
		int secs = seconds % 60;
		return String.format("%d:%02d", mins, secs);
	}
}

