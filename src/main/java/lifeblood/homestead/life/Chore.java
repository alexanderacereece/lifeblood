package lifeblood.homestead.life;

import lifeblood.entity.EntityNPC;

/** One chore a homesteader does start to finish: gathering wood, farming, a walk. ChoresBehavior picks and runs them. */
public interface Chore {
	/** Does a tick of the chore; returns true once it's finished (or can't be done), so the next one is picked. */
	boolean tick(EntityNPC npc);

	/** Called if the chore is cut short (the hour's over, a fight). */
	default void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	/** What they're doing, for /lb npc list. */
	String describe();

	/** What kind of chore it is, as the "doing" fact lines use: "wood", "farming", "fishing"... */
	String doing();
}
