package lifeblood.combat;

import lifeblood.ai.NpcBehaviors;

/**
 * Fight or flight, as an instinct every NPC has.
 */
public final class CombatSetup {
	private CombatSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.registerInstinct(CombatBehavior::new);
	}
}
