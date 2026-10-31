package npclaude.combat;

import npclaude.entity.EntityNPC;

/**
 * Allegiances between NPCs. For now every NPC belongs to the same placeholder faction, so all NPCs are allies;
 * this is where rivalries and alliances between factions will be decided later.
 */
public final class NpcFactions {
	public static final String DEFAULT = "default";

	private NpcFactions() {
	}

	public static boolean areAllies(EntityNPC a, EntityNPC b) {
		return a.getFaction().equals(b.getFaction());
	}
}
