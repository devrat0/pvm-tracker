package com.pvmtracker;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "PvM Tracker",
	description = "Tracks combat uptime, lost ticks, missed prayers, and avoidable damage during boss encounters.",
	tags = {"boss", "combat", "uptime", "tracker"}
)
public class BossPerformancePlugin extends Plugin
{
	private static final Set<String> BOSS_NAMES = Set.of(
		"Abyssal Sire", "Alchemical Hydra", "Cerberus", "Commander Zilyana", "General Graardor",
		"Kree'arra", "K'ril Tsutsaroth", "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
		"Giant Mole", "Kalphite Queen", "King Black Dragon", "Kraken", "Nex", "Nightmare",
		"Phosani's Nightmare", "Scurrius", "Skotizo", "Thermonuclear Smoke Devil", "Vorkath",
		"Zulrah", "Duke Sucellus", "The Whisperer", "Vardorvis", "Leviathan", "Phantom Muspah",
		"Corporeal Beast", "Great Olm", "Verzik Vitur", "Amascut", "Araxxor", "Sol Heredit", "TzTok-Jad"
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BossPerformanceConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BossPerformanceOverlay overlay;

	private BossPerformancePanel panel;
	private NavigationButton navButton;

	// Session State
	private CombatSession currentSession;
	private NPC trackedBossNpc;
	private final List<CombatSession> sessions = new ArrayList<>();
	
	// Tracking maps & sets
	private final Set<NPC> activeBossNpcs = new HashSet<>();
	private final Set<Projectile> processedProjectiles = new HashSet<>();
	private final List<PendingBossAttack> pendingBossAttacks = new ArrayList<>();

	// Player attack speed tracking
	private boolean playerAttackedThisTick = false;
	private int nextAvailableAttackTick = -1;
	private int lastPlayerAnimation = -1;
	private int lastCombatTick = 0;

	private static class PendingBossAttack
	{
		int tickLaunched;
		int tickLanded;
		CombatEvent.BossAttack.AttackStyle style;
		boolean correctPrayer;
		String playerPrayer;
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(BossPerformancePanel.class);
		panel.init(this);

		final BufferedImage icon = getPluginIcon();

		navButton = NavigationButton.builder()
			.tooltip("PvM Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		// Scan for already spawned bosses
		clientThread.invoke(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				for (NPC npc : client.getNpcs())
				{
					if (isBoss(npc))
					{
						activeBossNpcs.add(npc);
					}
				}
			}
		});

		log.debug("PvM Tracker started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		activeBossNpcs.clear();
		processedProjectiles.clear();
		pendingBossAttacks.clear();
		if (currentSession != null)
		{
			finalizeSession();
		}
		log.debug("PvM Tracker stopped!");
	}

	private BufferedImage getPluginIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(getClass(), "/pvmtracker_icon.png");
		}
		catch (Exception e)
		{
			// Fallback: Programmatically draw a target-like 16x16 icon
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setColor(new Color(255, 60, 60));
			g.drawOval(1, 1, 13, 13);
			g.drawOval(4, 4, 7, 7);
			g.fillOval(7, 7, 2, 2);
			g.dispose();
			return img;
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (isBoss(npc))
		{
			activeBossNpcs.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		activeBossNpcs.remove(npc);

		if (currentSession != null && npc == trackedBossNpc)
		{
			finalizeSession();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (currentSession == null)
		{
			return;
		}

		if (event.getActor() == trackedBossNpc || event.getActor() == client.getLocalPlayer())
		{
			finalizeSession();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			activeBossNpcs.clear();
			processedProjectiles.clear();
			pendingBossAttacks.clear();
			if (currentSession != null)
			{
				finalizeSession();
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		// 1. Check Player animations
		if (event.getActor() == client.getLocalPlayer())
		{
			int anim = event.getActor().getAnimation();
			if (anim != -1 && anim != lastPlayerAnimation && isPlayerAttackAnimation(anim))
			{
				playerAttackedThisTick = true;
				lastPlayerAnimation = anim;
			}
			else if (anim == -1)
			{
				lastPlayerAnimation = -1;
			}
			return;
		}

		// 2. Check Boss animations
		if (currentSession != null && event.getActor() == trackedBossNpc)
		{
			int anim = trackedBossNpc.getAnimation();
			if (anim == -1)
			{
				return;
			}

			CombatEvent.BossAttack.AttackStyle style = getStyleFromAnimation(anim);
			if (style != null)
			{
				int currentTick = client.getTickCount();
				int delay = 1;

				if (trackedBossNpc.getName().equals("TzTok-Jad"))
				{
					delay = 3;
				}

				int tickLanded = currentTick + delay;
				HeadIcon activePrayer = client.getLocalPlayer().getOverheadIcon();
				boolean correct = isCorrectPrayer(style, activePrayer);

				PendingBossAttack pending = new PendingBossAttack();
				pending.tickLaunched = currentTick;
				pending.tickLanded = tickLanded;
				pending.style = style;
				pending.correctPrayer = correct;
				pending.playerPrayer = getPrayerName(activePrayer);

				pendingBossAttacks.add(pending);
				log.debug("Logged pending boss attack: style={}, lands on {}", style, tickLanded);
			}
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (currentSession == null || trackedBossNpc == null)
		{
			return;
		}

		Projectile projectile = event.getProjectile();
		if (processedProjectiles.contains(projectile))
		{
			return;
		}

		// Verify target is local player
		if (projectile.getInteracting() != client.getLocalPlayer())
		{
			return;
		}

		processedProjectiles.add(projectile);

		CombatEvent.BossAttack.AttackStyle style = getStyleFromProjectile(projectile.getId());
		if (style != null)
		{
			int currentTick = client.getTickCount();
			int delay = (projectile.getEndCycle() - client.getGameCycle()) / 30;
			int tickLanded = currentTick + Math.max(1, delay);

			HeadIcon activePrayer = client.getLocalPlayer().getOverheadIcon();
			boolean correct = isCorrectPrayer(style, activePrayer);

			PendingBossAttack pending = new PendingBossAttack();
			pending.tickLaunched = currentTick;
			pending.tickLanded = tickLanded;
			pending.style = style;
			pending.correctPrayer = correct;
			pending.playerPrayer = getPrayerName(activePrayer);

			pendingBossAttacks.add(pending);
			log.debug("Logged pending boss projectile: style={}, lands on {}", style, tickLanded);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (currentSession == null || trackedBossNpc == null)
		{
			return;
		}

		// Check if damage is dealt to the boss (player hit the boss)
		if (event.getActor() == trackedBossNpc)
		{
			int damage = event.getHitsplat().getAmount();
			List<CombatEvent> events = currentSession.getEvents();
			for (int i = events.size() - 1; i >= 0; i--)
			{
				CombatEvent e = events.get(i);
				if (e instanceof CombatEvent.PlayerAttack)
				{
					CombatEvent.PlayerAttack pa = (CombatEvent.PlayerAttack) e;
					if (!pa.isLostTick())
					{
						if (pa.getDamage() == -1)
						{
							pa.setDamage(damage);
						}
						else
						{
							pa.setDamage(pa.getDamage() + damage);
						}
						break;
					}
				}
			}
			return;
		}

		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		int damage = hitsplat.getAmount();
		int currentTick = client.getTickCount();
		int relativeTick = currentTick - currentSession.getStartTick();

		// Match with pending boss attacks landing around this tick (allowing 1 tick tolerance)
		PendingBossAttack matched = null;
		for (PendingBossAttack pending : pendingBossAttacks)
		{
			if (Math.abs(pending.tickLanded - currentTick) <= 1)
			{
				matched = pending;
				break;
			}
		}

		if (matched != null)
		{
			pendingBossAttacks.remove(matched);
			currentSession.addEvent(new CombatEvent.BossAttack(
				relativeTick,
				matched.style,
				damage,
				matched.playerPrayer,
				matched.correctPrayer
			));
		}
		else
		{
			// Fallback unmatched hitsplat (e.g. environmental hazard or minion hit)
			HeadIcon activePrayer = client.getLocalPlayer().getOverheadIcon();
			boolean isAvoidable = damage > 0; // Unmatched damage is considered avoidable
			currentSession.addEvent(new CombatEvent.BossAttack(
				relativeTick,
				CombatEvent.BossAttack.AttackStyle.TYPELESS,
				damage,
				getPrayerName(activePrayer),
				!isAvoidable
			));
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int currentTick = client.getTickCount();

		// Cleanup expired projectiles & pending attacks
		processedProjectiles.removeIf(p -> client.getGameCycle() >= p.getEndCycle());
		pendingBossAttacks.removeIf(p -> currentTick > p.tickLanded);

		// 1. Session start checks
		if (currentSession == null)
		{
			NPC target = null;
			Actor interacting = client.getLocalPlayer().getInteracting();
			if (interacting instanceof NPC && isBoss((NPC) interacting))
			{
				target = (NPC) interacting;
			}
			else
			{
				for (NPC npc : activeBossNpcs)
				{
					if (npc.getInteracting() == client.getLocalPlayer())
					{
						target = npc;
						break;
					}
				}
			}

			if (target != null)
			{
				startSession(target);
			}
		}

		// 2. In-Session logic
		if (currentSession != null)
		{
			// Verify proximity and interaction
			boolean inCombat = false;
			if (trackedBossNpc != null)
			{
				if (client.getLocalPlayer().getInteracting() == trackedBossNpc || trackedBossNpc.getInteracting() == client.getLocalPlayer())
				{
					inCombat = true;
				}
				
				if (client.getLocalPlayer().getWorldLocation().distanceTo(trackedBossNpc.getWorldLocation()) > 30)
				{
					finalizeSession();
					return;
				}
			}

			if (inCombat)
			{
				lastCombatTick = currentTick;
			}

			// Inactivity timeout: 50 ticks (30s)
			if (currentTick - lastCombatTick > 50)
			{
				finalizeSession();
				return;
			}

			// Track attack uptime & lost ticks
			int speed = getEquippedWeaponSpeed();
			String weaponName = getEquippedWeaponName();

			if (playerAttackedThisTick)
			{
				// Catch up lost ticks before logging this attack
				if (nextAvailableAttackTick != -1 && currentTick > nextAvailableAttackTick)
				{
					for (int tick = nextAvailableAttackTick; tick < currentTick; tick++)
					{
						currentSession.addEvent(new CombatEvent.PlayerAttack(
							tick - currentSession.getStartTick(),
							weaponName,
							true,
							speed
						));
					}
				}

				// Log actual attack
				currentSession.addEvent(new CombatEvent.PlayerAttack(
					currentTick - currentSession.getStartTick(),
					weaponName,
					false,
					speed
				));

				nextAvailableAttackTick = currentTick + speed;
				playerAttackedThisTick = false;
			}
		}
	}

	private void startSession(NPC bossNpc)
	{
		currentSession = new CombatSession(bossNpc.getName(), client.getTickCount());
		trackedBossNpc = bossNpc;
		nextAvailableAttackTick = -1;
		playerAttackedThisTick = false;
		lastPlayerAnimation = -1;
		lastCombatTick = client.getTickCount();
		pendingBossAttacks.clear();

		log.debug("PvM Tracker session started for boss: {}", bossNpc.getName());
	}

	private void finalizeSession()
	{
		if (currentSession == null)
		{
			return;
		}

		int currentTick = client.getTickCount();
		currentSession.setEndTick(currentTick);

		// Record trailing lost ticks if session ends after attack cooldown expired
		if (nextAvailableAttackTick != -1 && currentTick > nextAvailableAttackTick)
		{
			int speed = getEquippedWeaponSpeed();
			String weapon = getEquippedWeaponName();
			for (int tick = nextAvailableAttackTick; tick < currentTick; tick++)
			{
				currentSession.addEvent(new CombatEvent.PlayerAttack(
					tick - currentSession.getStartTick(),
					weapon,
					true,
					speed
				));
			}
		}

		sessions.add(0, currentSession);
		if (sessions.size() > config.historyLimit())
		{
			sessions.remove(sessions.size() - 1);
		}

		SwingUtilities.invokeLater(() -> panel.updateSessionList(sessions));

		log.debug("PvM Tracker session finalized: boss={}, duration={} ticks", 
			currentSession.getBossName(), currentSession.getDurationTicks());

		currentSession = null;
		trackedBossNpc = null;
		nextAvailableAttackTick = -1;
		playerAttackedThisTick = false;
	}

	private boolean isBoss(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return false;
		}
		String name = npc.getName();
		if (BOSS_NAMES.contains(name))
		{
			return true;
		}
		if (!config.showOnlyBosses() && npc.getCombatLevel() >= config.trackMinCombatLevel())
		{
			return true;
		}
		return false;
	}

	private int getEquippedWeaponSpeed()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return 4;
		}
		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return 4; // Unarmed speed is 4
		}
		return WeaponSpeedLookup.getSpeed(weapon.getId(), getEquippedWeaponName());
	}

	private String getEquippedWeaponName()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return "Unarmed";
		}
		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (weapon == null)
		{
			return "Unarmed";
		}
		return itemManager.getItemComposition(weapon.getId()).getName();
	}

	private boolean isCorrectPrayer(CombatEvent.BossAttack.AttackStyle style, HeadIcon overhead)
	{
		if (style == CombatEvent.BossAttack.AttackStyle.TYPELESS)
		{
			return true;
		}
		if (overhead == null)
		{
			return false;
		}
		switch (style)
		{
			case MELEE:
				return overhead == HeadIcon.MELEE;
			case RANGE:
				return overhead == HeadIcon.RANGED;
			case MAGIC:
				return overhead == HeadIcon.MAGIC;
			default:
				return true;
		}
	}

	private String getPrayerName(HeadIcon overhead)
	{
		if (overhead == null)
		{
			return "None";
		}
		switch (overhead)
		{
			case MELEE:
				return "Protect from Melee";
			case RANGED:
				return "Protect from Ranged";
			case MAGIC:
				return "Protect from Magic";
			case REDEMPTION:
				return "Redemption";
			case SMITE:
				return "Smite";
			case RETRIBUTION:
				return "Retribution";
			default:
				return "Unknown";
		}
	}

	private CombatEvent.BossAttack.AttackStyle getStyleFromProjectile(int id)
	{
		switch (id)
		{
			case 2640: // Scurrius Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 2642: // Scurrius Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1477: // Vorkath Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1479: // Vorkath Magic
			case 1481: // Vorkath Fireball
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 1044: // Zulrah Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1046: // Zulrah Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 1242: // Cerberus Magic
			case 1243:
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 1245: // Cerberus Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1217: // Graardor Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1210: // K'ril Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 1197: // Kree Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 1198: // Kree Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
		}
		return null;
	}

	private CombatEvent.BossAttack.AttackStyle getStyleFromAnimation(int anim)
	{
		switch (anim)
		{
			case 2652: // Jad Ranged
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 2656: // Jad Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 2655: // Jad Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 11158: // Scurrius Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 11159: // Scurrius Magic fallback
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 11160: // Scurrius Ranged fallback
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 7951: // Vorkath Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 5806: // Zulrah Melee
			case 5807:
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 4491: // Cerberus Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 6964: // Zilyana Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 6967: // Zilyana Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 7060: // Graardor Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 7063: // Graardor Ranged slam
				return CombatEvent.BossAttack.AttackStyle.RANGE;
			case 6948: // K'ril Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
			case 6947: // K'ril Magic
				return CombatEvent.BossAttack.AttackStyle.MAGIC;
			case 6981: // Kree Melee
				return CombatEvent.BossAttack.AttackStyle.MELEE;
		}
		return null;
	}

	private boolean isPlayerAttackAnimation(int anim)
	{
		switch (anim)
		{
			// Melee/Fists
			case 422:
			case 423:
			// Slash/Stab/Crush
			case 386:
			case 390:
			case 401:
			case 412: // Whip
			case 419:
			case 428:
			case 440:
			case 1062:
			case 1379:
			case 1658:
			case 2062:
			case 2068:
			case 3294:
			case 3297:
			case 3298:
			case 3300:
			case 3761:
			case 3764:
			case 3776:
			case 3852:
			case 4001:
			case 4002:
			case 5084:
			case 7514:
			case 7515:
			case 7617: // Scythe
			case 7618:
			case 8056:
			case 8145:
			case 8288:
			case 9471:
			case 9493:
			case 10989:
			case 10990:
			// Ranged
			case 426: // Bow
			case 5061: // Blowpipe
			case 7552:
			case 7555:
			case 7615: // Crossbow
			case 9964:
			case 9961:
			// Magic
			case 1162:
			case 1167:
			case 7855:
			case 811:
			case 1978:
			case 1979:
			case 9447:
				return true;
			default:
				return false;
		}
	}

	public CombatSession getCurrentSession()
	{
		return currentSession;
	}

	public int getNextAvailableAttackTick()
	{
		return nextAvailableAttackTick;
	}

	@Provides
	BossPerformanceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossPerformanceConfig.class);
	}
}
