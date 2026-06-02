package com.pvmtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BossPerformanceOverlay extends OverlayPanel
{
	private final Client client;
	private final BossPerformancePlugin plugin;
	private final BossPerformanceConfig config;

	@Inject
	public BossPerformanceOverlay(Client client, BossPerformancePlugin plugin, BossPerformanceConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		CombatSession session = plugin.getCurrentSession();
		if (session == null)
		{
			return null;
		}

		int currentTick = client.getTickCount();
		double uptime = session.getUptimePercentActive(currentTick);
		double prayerAcc = session.getPrayerAccuracy();
		String time = session.getFormattedDurationActive(currentTick);

		panelComponent.getChildren().clear();

		// Line 1: Boss Name & Timer
		panelComponent.getChildren().add(LineComponent.builder()
			.left(session.getBossName())
			.leftColor(Color.WHITE)
			.right(time)
			.rightColor(Color.LIGHT_GRAY)
			.build());

		// Line 2: Uptime & Prayer Accuracy side-by-side
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Upt: " + String.format("%.1f%%", uptime))
			.leftColor(getStatColor(uptime))
			.right("Pray: " + String.format("%.1f%%", prayerAcc))
			.rightColor(getStatColor(prayerAcc))
			.build());

		return super.render(graphics);
	}

	private Color getStatColor(double value)
	{
		if (value >= 95.0)
		{
			return Color.GREEN;
		}
		else if (value >= 80.0)
		{
			return Color.YELLOW;
		}
		else
		{
			return Color.RED;
		}
	}
}
