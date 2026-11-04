package lifeblood.ai;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.entity.EntityNPC;

/**
 * One thing an NPC can be doing. The brain runs the highest-priority behavior that is able to run,
 * so behaviors stack: e.g. a "flee danger" behavior can sit above "follow" and take over when needed.
 *
 * Behaviors are saved with the NPC. Every behavior must be registered in {@link NpcBehaviors}
 * under its id, with a no-argument constructor, so it can be recreated on load.
 */
public interface NpcBehavior {
	String getId();

	/** Higher runs first. */
	default int getPriority() {
		return 0;
	}

	boolean canStart(EntityNPC npc);

	default boolean shouldContinue(EntityNPC npc) {
		return this.canStart(npc);
	}

	default void start(EntityNPC npc) {
	}

	void tick(EntityNPC npc);

	default void stop(EntityNPC npc) {
	}

	default void save(CompoundTag tag) {
	}

	default void load(CompoundTag tag) {
	}

	/** Short human-readable summary, shown by /npc list. */
	default String describe() {
		return this.getId();
	}
}
