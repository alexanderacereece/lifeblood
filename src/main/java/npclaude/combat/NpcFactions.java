package npclaude.combat;

import java.util.Locale;
import npclaude.entity.EntityNPC;

/**
 * Allegiance checks between NPCs, answered by the world's {@link FactionRegistry}.
 * Players are treated as everyone's ally for now.
 */
public final class NpcFactions {
	public static final String DEFAULT = FactionRegistry.DEFAULT;

	private NpcFactions() {
	}

	/** Cleans up a saved faction name. Saves from before factions existed used "default", which became the settlers. */
	public static String normalize(String faction) {
		String name = faction.toLowerCase(Locale.ROOT);
		return name.isEmpty() || name.equals("default") ? DEFAULT : name;
	}

	public static FactionRelation relation(EntityNPC a, EntityNPC b) {
		return FactionRegistry.get(a.world).getRelation(a.getFaction(), b.getFaction());
	}

	public static boolean areAllies(EntityNPC a, EntityNPC b) {
		return relation(a, b) == FactionRelation.ALLY;
	}

	public static boolean areEnemies(EntityNPC a, EntityNPC b) {
		return relation(a, b) == FactionRelation.HOSTILE;
	}
}
