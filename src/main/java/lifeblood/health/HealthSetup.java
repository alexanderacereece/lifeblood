package lifeblood.health;

import lifeblood.ai.NpcBehaviors;

/**
 * Going home to recover after being downed, as an instinct every NPC has.
 */
public final class HealthSetup {
	private HealthSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.registerInstinct(RecoverBehavior::new);
	}
}
