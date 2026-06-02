package com.pvmtracker;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.game.SpriteManager;

public class BossPerformancePanel extends PluginPanel
{
	private static final String LIST_CARD = "LIST";
	private static final String DETAIL_CARD = "DETAIL";
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel container = new JPanel(cardLayout);

	// List View components
	private final JPanel listPanel = new JPanel();
	private final JScrollPane listScrollPane;

	// Detail View components
	private final JPanel detailPanel = new JPanel();
	private final JLabel detailTitle = new JLabel();
	private final JLabel detailDuration = new JLabel();
	private final JLabel detailUptime = new JLabel();
	private final JLabel detailLostTicks = new JLabel();
	private final JLabel detailMissedPrayers = new JLabel();
	private final JLabel detailAvoidableDamage = new JLabel();
	private final CombatTimelineView timelineView = new CombatTimelineView();
	private final JScrollPane timelineScrollPane;

	private BossPerformancePlugin plugin;

	@Inject
	public BossPerformancePanel(SpriteManager spriteManager)
	{
		super(false); // Do not put scrollbar around the root plugin panel, we handle scrollable areas ourselves.

		timelineView.setSpriteManager(spriteManager);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Initialize List Panel
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		listScrollPane = new JScrollPane(listPanel);
		listScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listScrollPane.setBorder(null);
		listScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel listContainer = new JPanel(new BorderLayout());
		listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		
		JLabel titleLabel = new JLabel("PvM Tracker", SwingConstants.CENTER);
		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setBorder(new EmptyBorder(10, 0, 15, 0));
		listContainer.add(titleLabel, BorderLayout.NORTH);
		listContainer.add(listScrollPane, BorderLayout.CENTER);

		// Initialize Detail Panel
		detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
		detailPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detailPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Back Button
		JButton backButton = new JButton("◀ Back to History");
		backButton.setFont(new Font("SansSerif", Font.BOLD, 11));
		backButton.setFocusPainted(false);
		backButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		backButton.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		backButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		backButton.addActionListener(e -> cardLayout.show(container, LIST_CARD));
		backButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		backButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		
		// Detail Stats
		JPanel statsPanel = new JPanel(new GridLayout(5, 2, 5, 3));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
		statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));

		detailTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
		detailTitle.setForeground(Color.WHITE);
		detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		addStatRow(statsPanel, "Duration:", detailDuration);
		addStatRow(statsPanel, "Attack Uptime:", detailUptime);
		addStatRow(statsPanel, "Ticks Lost:", detailLostTicks);
		addStatRow(statsPanel, "Missed Prayers:", detailMissedPrayers);
		addStatRow(statsPanel, "Avoidable Dmg:", detailAvoidableDamage);

		// Timeline ScrollPane (vertical scrolling)
		timelineScrollPane = new JScrollPane(timelineView);
		timelineScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		timelineScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		timelineScrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()));
		timelineScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		timelineScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
		timelineScrollPane.setPreferredSize(new Dimension(210, 240));

		detailPanel.add(backButton);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		detailPanel.add(detailTitle);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		detailPanel.add(statsPanel);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 8)));
		
		JLabel timelineLabel = new JLabel("Combat Timeline (Tick-by-Tick)");
		timelineLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		timelineLabel.setForeground(Color.WHITE);
		timelineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		timelineLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		
		detailPanel.add(timelineLabel);
		detailPanel.add(Box.createRigidArea(new Dimension(0, 4)));
		detailPanel.add(timelineScrollPane);
		detailPanel.add(Box.createVerticalGlue());

		// Assemble Container
		container.add(listContainer, LIST_CARD);
		container.add(detailPanel, DETAIL_CARD);

		add(container, BorderLayout.CENTER);
		cardLayout.show(container, LIST_CARD);
	}

	public void init(BossPerformancePlugin plugin)
	{
		this.plugin = plugin;
	}

	private void addStatRow(JPanel panel, String labelText, JLabel valLabel)
	{
		JLabel lbl = new JLabel(labelText);
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
		panel.add(lbl);

		valLabel.setForeground(Color.WHITE);
		valLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		panel.add(valLabel);
	}

	public void updateSessionList(List<CombatSession> sessions)
	{
		listPanel.removeAll();

		if (sessions.isEmpty())
		{
			JLabel noSessions = new JLabel("No sessions tracked yet.", SwingConstants.CENTER);
			noSessions.setFont(new Font("SansSerif", Font.ITALIC, 11));
			noSessions.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			noSessions.setAlignmentX(Component.CENTER_ALIGNMENT);
			listPanel.add(Box.createRigidArea(new Dimension(0, 40)));
			listPanel.add(noSessions);
		}
		else
		{
			for (CombatSession session : sessions)
			{
				listPanel.add(createSessionRow(session));
				listPanel.add(Box.createRigidArea(new Dimension(0, 4)));
			}
			listPanel.add(Box.createVerticalGlue());
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private JPanel createSessionRow(CombatSession session)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR.brighter()),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)
		));
		row.setCursor(new Cursor(Cursor.HAND_CURSOR));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setOpaque(false);

		JLabel nameLabel = new JLabel(session.getBossName());
		nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
		nameLabel.setForeground(Color.WHITE);

		JLabel timeLabel = new JLabel(TIME_FORMAT.format(session.getStartTime()) + " (" + session.getFormattedDuration() + ")");
		timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
		timeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		leftPanel.add(nameLabel);
		leftPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		leftPanel.add(timeLabel);

		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setOpaque(false);

		JLabel uptimeLabel = new JLabel(String.format("Uptime: %.1f%%", session.getUptimePercent()));
		uptimeLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		uptimeLabel.setForeground(getUptimeColor(session.getUptimePercent()));
		uptimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JLabel dmgLabel = new JLabel("Dmg Avoided: " + session.getAvoidableDamage());
		dmgLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
		dmgLabel.setForeground(session.getMissedPrayers() > 0 ? new Color(255, 82, 82) : ColorScheme.LIGHT_GRAY_COLOR);
		dmgLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		rightPanel.add(uptimeLabel);
		rightPanel.add(Box.createRigidArea(new Dimension(0, 2)));
		rightPanel.add(dmgLabel);

		row.add(leftPanel, BorderLayout.WEST);
		row.add(rightPanel, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showDetailView(session);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return row;
	}

	private void showDetailView(CombatSession session)
	{
		detailTitle.setText(session.getBossName());
		detailDuration.setText(session.getFormattedDuration() + " (" + session.getDurationTicks() + " ticks)");
		detailUptime.setText(String.format("%.1f%%", session.getUptimePercent()));
		detailUptime.setForeground(getUptimeColor(session.getUptimePercent()));
		detailLostTicks.setText(session.getTotalLostTicks() + " ticks");
		detailMissedPrayers.setText(session.getMissedPrayers() + " attacks");
		detailMissedPrayers.setForeground(session.getMissedPrayers() > 0 ? new Color(255, 82, 82) : Color.WHITE);
		detailAvoidableDamage.setText(session.getAvoidableDamage() + " hp");
		detailAvoidableDamage.setForeground(session.getAvoidableDamage() > 0 ? new Color(255, 82, 82) : Color.WHITE);

		timelineView.setSession(session);

		cardLayout.show(container, DETAIL_CARD);
	}

	private Color getUptimeColor(double uptime)
	{
		if (uptime >= 95.0)
		{
			return new Color(0, 230, 118); // Bright green
		}
		else if (uptime >= 80.0)
		{
			return new Color(255, 215, 0); // Yellow/Gold
		}
		else
		{
			return new Color(255, 82, 82); // Bright red
		}
	}
}
