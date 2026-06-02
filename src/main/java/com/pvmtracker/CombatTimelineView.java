package com.pvmtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;

public class CombatTimelineView extends JPanel
{
	private static final int TICK_WIDTH = 22;
	private static final int ROW_HEIGHT = 28;
	private static final int LABEL_ROW_HEIGHT = 18;
	private static final int LEFT_PADDING = 10;
	private static final int PANEL_HEIGHT = ROW_HEIGHT * 2 + LABEL_ROW_HEIGHT + 15;

	private CombatSession session;
	private CombatEvent.PlayerAttack[] playerEvents;
	private CombatEvent.BossAttack[] bossEvents;

	public CombatTimelineView()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ToolTipManager.sharedInstance().registerComponent(this);
		ToolTipManager.sharedInstance().setInitialDelay(50);
		ToolTipManager.sharedInstance().setDismissDelay(10000);
	}

	public void setSession(CombatSession session)
	{
		this.session = session;
		if (session == null)
		{
			playerEvents = null;
			bossEvents = null;
			setPreferredSize(new Dimension(0, PANEL_HEIGHT));
			revalidate();
			repaint();
			return;
		}

		int duration = Math.max(1, session.getDurationTicks());
		playerEvents = new CombatEvent.PlayerAttack[duration];
		bossEvents = new CombatEvent.BossAttack[duration];

		for (CombatEvent e : session.getEvents())
		{
			int tick = e.getGameTick();
			if (tick >= 0 && tick < duration)
			{
				if (e instanceof CombatEvent.PlayerAttack)
				{
					playerEvents[tick] = (CombatEvent.PlayerAttack) e;
				}
				else if (e instanceof CombatEvent.BossAttack)
				{
					bossEvents[tick] = (CombatEvent.BossAttack) e;
				}
			}
		}

		int width = duration * TICK_WIDTH + LEFT_PADDING * 2;
		setPreferredSize(new Dimension(width, PANEL_HEIGHT));
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (session == null || playerEvents == null || bossEvents == null)
		{
			return;
		}

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int duration = session.getDurationTicks();
		Font labelFont = new Font("SansSerif", Font.PLAIN, 10);
		Font boldFont = new Font("SansSerif", Font.BOLD, 10);

		// Grid & Tracks
		for (int tick = 0; tick < duration; tick++)
		{
			int x = LEFT_PADDING + tick * TICK_WIDTH;
			
			// 1. Draw Player Track (Top row)
			int playerY = 5;
			CombatEvent.PlayerAttack pa = playerEvents[tick];
			if (pa != null)
			{
				if (pa.isLostTick())
				{
					// Lost Tick (Uptime gap)
					g2.setColor(new Color(183, 28, 28, 120)); // Red tint
					g2.fillRect(x + 1, playerY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
					g2.setColor(new Color(183, 28, 28));
					g2.drawRect(x + 1, playerY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
				}
				else
				{
					// Actual Attack launched
					g2.setColor(new Color(0, 188, 212)); // Cyan
					g2.fillRect(x + 1, playerY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
					
					// Draw beautiful vector sword pointing upwards
					g2.setColor(Color.WHITE);
					g2.fillRect(x + 10, playerY + 5, 2, 11); // Blade
					g2.fillRect(x + 7, playerY + 13, 8, 2);  // Crossguard
					g2.setColor(new Color(139, 69, 19));     // Brown hilt
					g2.fillRect(x + 10, playerY + 15, 2, 4); // Pommel/hilt
				}
			}
			else
			{
				// Inactive cooldown
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.fillRect(x + 1, playerY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
			}

			// 2. Draw Tick Label Track (Middle row)
			int labelY = playerY + ROW_HEIGHT;
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.setFont(labelFont);
			if (tick % 5 == 0)
			{
				g2.drawString(String.valueOf(tick), x + 3, labelY + 12);
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.drawLine(x + TICK_WIDTH / 2, labelY, x + TICK_WIDTH / 2, labelY + 5);
			}
			else
			{
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.drawLine(x + TICK_WIDTH / 2, labelY + 6, x + TICK_WIDTH / 2, labelY + 10);
			}

			// 3. Draw Boss Track (Bottom row)
			int bossY = labelY + LABEL_ROW_HEIGHT;
			CombatEvent.BossAttack ba = bossEvents[tick];
			if (ba != null)
			{
				Color styleColor;
				switch (ba.getAttackStyle())
				{
					case MAGIC:
						styleColor = new Color(25, 118, 210); // Blue
						break;
					case RANGE:
						styleColor = new Color(56, 142, 60); // Green
						break;
					case MELEE:
						styleColor = new Color(216, 67, 21); // Orange
						break;
					default:
						styleColor = Color.LIGHT_GRAY;
						break;
				}

				g2.setColor(styleColor);
				g2.fillRect(x + 1, bossY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);

				// Draw Attack Icon based on style (Upper half of the box)
				int iconY = bossY + 3;
				switch (ba.getAttackStyle())
				{
					case MAGIC:
						// Draw magic blue sparkle circle
						g2.setColor(new Color(135, 206, 250)); // Light Blue Sparkle
						g2.fillOval(x + 6, iconY + 2, 10, 10);
						g2.setColor(Color.WHITE);
						g2.drawOval(x + 6, iconY + 2, 10, 10);
						break;
					case RANGE:
						// Draw green arrow pointing down
						g2.setColor(new Color(144, 238, 144)); // Light Green Arrow
						g2.fillRect(x + 10, iconY + 1, 2, 6); // shaft
						int[] ax = { x + 6, x + 11, x + 16 };
						int[] ay = { iconY + 6, iconY + 12, iconY + 6 };
						g2.fillPolygon(ax, ay, 3); // tip
						break;
					case MELEE:
						// Draw red crossed swords
						g2.setColor(new Color(255, 128, 128)); // Light Red Swords
						g2.drawLine(x + 5, iconY + 2, x + 16, iconY + 11);
						g2.drawLine(x + 16, iconY + 2, x + 5, iconY + 11);
						break;
					default:
						// Typeless: small white dot
						g2.setColor(Color.WHITE);
						g2.fillOval(x + 9, iconY + 4, 4, 4);
						break;
				}

				// Draw damage text at the bottom half of the box
				if (ba.getDamage() > 0)
				{
					g2.setColor(Color.WHITE);
					g2.setFont(boldFont);
					String dmgStr = String.valueOf(ba.getDamage());
					int offset = dmgStr.length() > 1 ? 4 : 7;
					g2.drawString(dmgStr, x + offset, bossY + 25);
				}

				// If prayer was missed, highlight with thick red borders
				if (!ba.isCorrectPrayer())
				{
					g2.setColor(Color.RED);
					g2.drawRect(x + 1, bossY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
					g2.drawRect(x + 2, bossY + 2, TICK_WIDTH - 4, ROW_HEIGHT - 4);
					
					// Draw a small red indicator at the top of the box
					g2.setColor(Color.RED);
					g2.fillRect(x + 2, bossY + 2, TICK_WIDTH - 4, 3);
				}
			}
			else
			{
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.fillRect(x + 1, bossY + 1, TICK_WIDTH - 2, ROW_HEIGHT - 2);
			}
		}
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (session == null || playerEvents == null || bossEvents == null)
		{
			return null;
		}

		int x = event.getX();
		int y = event.getY();

		int tick = (x - LEFT_PADDING) / TICK_WIDTH;
		if (tick < 0 || tick >= session.getDurationTicks())
		{
			return null;
		}

		StringBuilder sb = new StringBuilder("<html><body style='padding: 5px; font-family: sans-serif;'>");
		sb.append("<b style='color:#ffffff;'>Tick: ").append(tick).append("</b> (").append(String.format("%.1fs", tick * 0.6)).append(")<br>");

		CombatEvent.PlayerAttack pa = playerEvents[tick];
		if (pa != null)
		{
			if (pa.isLostTick())
			{
				sb.append("<span style='color:#ff5252;'><b>Lost Tick (Uptime Gap)</b></span><br>");
			}
			else
			{
				sb.append("<span style='color:#00e5ff;'><b>Player Attack:</b> ").append(pa.getWeaponName())
					.append("</span> (").append(pa.getAttackSpeed()).append("t)<br>");
			}
		}

		CombatEvent.BossAttack ba = bossEvents[tick];
		if (ba != null)
		{
			String styleColor = getStyleColorHtml(ba.getAttackStyle());
			sb.append("<span style='color:").append(styleColor).append(";'><b>Boss Attack:</b> ")
				.append(ba.getAttackStyle().getName()).append("</span><br>");
			sb.append("Damage Taken: ").append(ba.getDamage()).append("<br>");
			sb.append("Overhead Prayer: ").append(ba.getPlayerPrayer()).append(" ");
			if (ba.isCorrectPrayer())
			{
				sb.append("<span style='color:#00e676;'>(Correct)</span>");
			}
			else
			{
				sb.append("<span style='color:#ff1744;'><b>(MISSED)</b></span>");
			}
			sb.append("<br>");
		}

		sb.append("</body></html>");
		return sb.toString();
	}

	private String getStyleColorHtml(CombatEvent.BossAttack.AttackStyle style)
	{
		switch (style)
		{
			case MAGIC:
				return "#29b6f6";
			case RANGE:
				return "#66bb6a";
			case MELEE:
				return "#ff7043";
			default:
				return "#e0e0e0";
		}
	}
}
