package lifeblood.people;

import lifeblood.entity.EntityNPC;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;
import lifeblood.world.WorldSystems;

/**
 * Keeping the register of people tidy, and recording deaths in it.
 */
public final class PeopleSetup {
	private PeopleSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		WorldSystems.register(PeopleRegistry.UPKEEP);
		LifeEvents.listen(DeathEvent.class, event -> {
			if (event.victim() instanceof EntityNPC npc) {
				PeopleRegistry.get(npc.world).died(npc.getUuid());
			}
		});
	}
}
