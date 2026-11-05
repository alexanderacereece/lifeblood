package lifeblood.brawl;

import lifeblood.ai.NpcBehaviors;

/**
 * Brawling, as an instinct every NPC has.
 */
public final class BrawlSetup {
	private BrawlSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.registerInstinct(BrawlBehavior::new);
	}
}
