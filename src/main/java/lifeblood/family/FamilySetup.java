package lifeblood.family;

import lifeblood.ai.NpcBehaviors;

/**
 * A child keeping near a parent, as an instinct every NPC has (it only does anything while they're a child).
 */
public final class FamilySetup {
	private FamilySetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.registerInstinct(StayNearParentBehavior::new);
	}
}
