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
	private static final int TICK_X = 10;
	private static final int PLAYER_X = 55;
	private static final int BOSS_X = 100;
	
	private static final int PLAYER_WIDTH = 26;
	private static final int BOSS_WIDTH = 46;
	
	private static final int TICK_HEIGHT = 20;
	private static final int HEADER_HEIGHT = 22;
	private static final int LEFT_PADDING = 10;

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
			setPreferredSize(new Dimension(0, 0));
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

		int height = HEADER_HEIGHT + duration * TICK_HEIGHT + 10;
		// Width of 165 is perfect to fit in a narrow sidebar without horizontal scrollbars
		setPreferredSize(new Dimension(165, height));
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

		// Draw headers
		g2.setFont(boldFont);
		g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g2.drawString("Tick", TICK_X, 15);
		g2.drawString("Player", PLAYER_X, 15);
		g2.drawString("Boss", BOSS_X, 15);
		
		g2.setColor(ColorScheme.DARK_GRAY_COLOR);
		g2.drawLine(LEFT_PADDING, HEADER_HEIGHT - 2, getWidth() - LEFT_PADDING, HEADER_HEIGHT - 2);

		for (int tick = 0; tick < duration; tick++)
		{
			int y = HEADER_HEIGHT + tick * TICK_HEIGHT;

			// 1. Draw Tick Label
			g2.setFont(labelFont);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawString(String.valueOf(tick), TICK_X, y + 13);

			// 2. Draw Player Block
			CombatEvent.PlayerAttack pa = playerEvents[tick];
			if (pa != null)
			{
				if (pa.isLostTick())
				{
					// Lost Tick (Uptime gap)
					g2.setColor(new Color(183, 28, 28, 120)); // Red tint
					g2.fillRect(PLAYER_X, y + 1, PLAYER_WIDTH, TICK_HEIGHT - 2);
					g2.setColor(new Color(183, 28, 28));
					g2.drawRect(PLAYER_X, y + 1, PLAYER_WIDTH, TICK_HEIGHT - 2);
				}
				else
				{
					// Actual Attack launched
					g2.setColor(new Color(0, 188, 212)); // Cyan
					g2.fillRect(PLAYER_X, y + 1, PLAYER_WIDTH, TICK_HEIGHT - 2);
					
					// Draw small vector sword
					int swordOffsetX = pa.getDamage() != -1 ? 1 : 9;
					g2.setColor(Color.WHITE);
					g2.fillRect(PLAYER_X + swordOffsetX + 3, y + 4, 1, 8); // Blade
					g2.fillRect(PLAYER_X + swordOffsetX + 1, y + 10, 5, 1); // Guard
					g2.setColor(new Color(139, 69, 19));
					g2.fillRect(PLAYER_X + swordOffsetX + 3, y + 11, 1, 2); // Hilt

					// Draw damage text on right side
					if (pa.getDamage() != -1)
					{
						g2.setColor(Color.WHITE);
						g2.setFont(boldFont);
						g2.drawString(String.valueOf(pa.getDamage()), PLAYER_X + 10, y + 13);
					}
				}
			}
			else
			{
				// Inactive cooldown
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.fillRect(PLAYER_X, y + 1, PLAYER_WIDTH, TICK_HEIGHT - 2);
			}

			// 3. Draw Boss Block
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
				g2.fillRect(BOSS_X, y + 1, BOSS_WIDTH, TICK_HEIGHT - 2);

				// Draw Protect Prayer Icon based on style (Left side of the box)
				int iconX = BOSS_X + 4;
				int iconY = y + 2;
				switch (ba.getAttackStyle())
				{
					case MAGIC:
						// Magic Overhead: blue circle background, yellow wizard hat
						g2.setColor(new Color(25, 118, 210)); // Blue background
						g2.fillOval(iconX, iconY + 2, 10, 10);
						
						g2.setColor(new Color(255, 215, 0)); // Gold hat
						int[] hatX = { iconX + 2, iconX + 5, iconX + 8 };
						int[] hatY = { iconY + 9, iconY + 4, iconY + 9 };
						g2.fillPolygon(hatX, hatY, 3);
						g2.fillRect(iconX + 1, iconY + 9, 9, 1); // hat brim
						break;
						
					case RANGE:
						// Range Overhead: green circle background, arrow pointing down
						g2.setColor(new Color(56, 142, 60)); // Green background
						g2.fillOval(iconX, iconY + 2, 10, 10);
						
						g2.setColor(new Color(0, 230, 118)); // Bright Green arrow
						g2.fillRect(iconX + 4, iconY + 4, 2, 4); // shaft
						int[] arrX = { iconX + 2, iconX + 5, iconX + 8 };
						int[] arrY = { iconY + 7, iconY + 10, iconY + 7 };
						g2.fillPolygon(arrX, arrY, 3); // arrow head
						break;
						
					case MELEE:
						// Melee Overhead: red/orange circle background, sword pointing down
						g2.setColor(new Color(216, 67, 21)); // Red/Orange background
						g2.fillOval(iconX, iconY + 2, 10, 10);
						
						g2.setColor(new Color(255, 82, 82)); // Bright Red sword
						g2.fillRect(iconX + 4, iconY + 3, 2, 6); // blade
						g2.fillRect(iconX + 2, iconY + 8, 6, 1); // guard
						g2.setColor(Color.WHITE);
						g2.fillRect(iconX + 4, iconY + 9, 2, 1); // hilt
						break;
						
					default:
						// Typeless: small white dot
						g2.setColor(Color.WHITE);
						g2.fillOval(iconX + 3, iconY + 4, 3, 3);
						break;
				}

				// Draw damage text (Right side of the box)
				if (ba.getDamage() > 0)
				{
					g2.setColor(Color.WHITE);
					g2.setFont(boldFont);
					String dmgStr = String.valueOf(ba.getDamage());
					g2.drawString(dmgStr, BOSS_X + 20, y + 13);
				}

				// If prayer was missed, highlight with thick red borders
				if (!ba.isCorrectPrayer())
				{
					g2.setColor(Color.RED);
					g2.drawRect(BOSS_X, y + 1, BOSS_WIDTH, TICK_HEIGHT - 2);
					g2.drawRect(BOSS_X + 1, y + 2, BOSS_WIDTH - 2, TICK_HEIGHT - 4);
					
					// Draw a small red indicator at the top of the box
					g2.setColor(Color.RED);
					g2.fillRect(BOSS_X + 1, y + 2, BOSS_WIDTH - 2, 2);
				}
			}
			else
			{
				g2.setColor(ColorScheme.DARK_GRAY_COLOR);
				g2.fillRect(BOSS_X, y + 1, BOSS_WIDTH, TICK_HEIGHT - 2);
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

		int tick = (y - HEADER_HEIGHT) / TICK_HEIGHT;
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
					.append("</span> (").append(pa.getAttackSpeed()).append("t)");
				if (pa.getDamage() != -1)
				{
					sb.append(" - Hit: <b style='color:#ffffff;'>").append(pa.getDamage()).append("</b>");
				}
				sb.append("<br>");
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
