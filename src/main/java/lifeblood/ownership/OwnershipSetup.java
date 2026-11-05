package lifeblood.ownership;

import lifeblood.world.WorldSystem;
import lifeblood.world.WorldSystems;

/**
 * Forgetting, each tick, any placer left over by an action that never finished (see BlockOwners.placingAs).
 */
public final class OwnershipSetup {
	private OwnershipSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		WorldSystems.register(WorldSystem.every(1, world -> BlockOwners.resetPlacers()));
	}
}
