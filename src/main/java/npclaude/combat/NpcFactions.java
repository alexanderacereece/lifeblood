package npclaude.combat;

import java.util.List;
import java.util.Locale;
import npclaude.entity.EntityNPC;

/**
 * Allegiances between NPCs. Two placeholder factions exist for testing: NPCs of the same faction are allies,
 * NPCs of different factions are enemies. Players are treated as everyone's ally for now.
 * Real allegiance rules (neutral factions, reputations, alliances) will be decided here later.
 */
public final class NpcFactions {
	public static final String SETTLERS = "settlers";
	public static final String RAIDERS = "raiders";
	public static final String DEFAULT = SETTLERS;
	public static final List<String> ALL = List.of(SETTLERS, RAIDERS);

	private NpcFactions() {
	}

	public static boolean isKnown(String faction) {
		return ALL.contains(faction);
	}

	/** Maps saved or typed names onto a known faction; anything unknown (including the old "default") joins the default. */
	public static String normalize(String faction) {
		String name = faction.toLowerCase(Locale.ROOT);
		return isKnown(name) ? name : DEFAULT;
	}

	public static boolean areAllies(EntityNPC a, EntityNPC b) {
		return a.getFaction().equals(b.getFaction());
	}

	public static boolean areEnemies(EntityNPC a, EntityNPC b) {
		return !areAllies(a, b);
	}
}
