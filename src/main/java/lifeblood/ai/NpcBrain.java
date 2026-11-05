package lifeblood.ai;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.entity.player.Player;
import lifeblood.Lifeblood;
import lifeblood.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * Picks which behavior an NPC runs each tick: the highest-priority one that can run.
 * Switching stops the old behavior and starts the new one. Three layers, in order:
 *
 * 1. Instincts (combat, brawls, recovering...) are built into every NPC: they are never replaced or saved.
 * 2. Behaviors are the NPC's orders (follow, stay, go somewhere), set by players and commands, and saved.
 * 3. With no orders that can run, the NPC goes about its day: its routine's activity for the time of day.
 */
public class NpcBrain {
	/** How often the distance-based thinking rate is recalculated, in ticks. */
	private static final int THINK_RATE_REFRESH = 20;

	private final EntityNPC npc;
	private final List<NpcBehavior> instincts = new ArrayList<>();
	private final List<NpcBehavior> behaviors = new ArrayList<>();
	/** Instincts and behaviors together, highest priority first. */
	private final List<NpcBehavior> candidates = new ArrayList<>();
	@Nullable
	private NpcBehavior active;
	/** Thinks every this many ticks; 0 until first worked out. */
	private int thinkInterval;

	public NpcBrain(EntityNPC npc) {
		this.npc = npc;
	}

	public void addInstinct(NpcBehavior instinct) {
		this.instincts.add(instinct);
		this.rebuildCandidates();
	}

	public void add(NpcBehavior behavior) {
		this.behaviors.add(behavior);
		this.rebuildCandidates();
	}

	/** Removes all orders (instincts stay), so the NPC goes back to its routine. */
	public void clear() {
		if (this.active != null && this.behaviors.contains(this.active)) {
			this.active.stop(this.npc);
			this.active = null;
		}

		this.behaviors.clear();
		this.rebuildCandidates();
	}

	private void rebuildCandidates() {
		this.candidates.clear();
		this.candidates.addAll(this.instincts);
		this.candidates.addAll(this.behaviors);
		this.candidates.sort(Comparator.comparingInt(NpcBehavior::getPriority).reversed());
	}

	/** Replaces every behavior with the given ones. */
	public void set(NpcBehavior... behaviors) {
		this.clear();
		for (NpcBehavior behavior : behaviors) {
			this.add(behavior);
		}
	}

	public List<NpcBehavior> getBehaviors() {
		return Collections.unmodifiableList(this.behaviors);
	}

	/** The first behavior (not instinct) of the given type, if the NPC has one. */
	@Nullable
	public <T extends NpcBehavior> T findBehavior(Class<T> type) {
		for (NpcBehavior behavior : this.behaviors) {
			if (type.isInstance(behavior)) {
				return type.cast(behavior);
			}
		}

		return null;
	}

	@Nullable
	public NpcBehavior getActive() {
		return this.active;
	}

	/** Whether it's going about its routine, with no orders or instincts in charge. */
	public boolean isOnRoutine() {
		return this.active != null && this.active == this.npc.routine.currentBehavior();
	}

	/** Runs {@link #tick} on this NPC's thinking ticks, which come less often the further it is from every player. */
	public void think() {
		if (this.isThinkTick()) {
			this.tick();
		}
	}

	/**
	 * NPCs far from every player decide what to do less often (their movement stays smooth every tick):
	 * every tick within 32 blocks, every 2nd within 64, every 5th within 128, every 10th beyond.
	 * Each NPC thinks on its own offset tick so the work is spread out.
	 */
	private boolean isThinkTick() {
		if (this.npc.tickCount % THINK_RATE_REFRESH == 0 || this.thinkInterval == 0) {
			Player nearest = this.npc.world.getClosestPlayerToEntity(this.npc, -1.0);
			double distance = nearest != null ? nearest.distanceTo(this.npc) : Double.MAX_VALUE;
			this.thinkInterval = distance < 32.0 ? 1 : distance < 64.0 ? 2 : distance < 128.0 ? 5 : 10;
		}

		return (this.npc.tickCount + this.npc.id) % this.thinkInterval == 0;
	}

	/** Picks and runs the behavior for this tick; {@link #think} calls it on thinking ticks. */
	public void tick() {
		NpcBehavior next = null;
		for (NpcBehavior behavior : this.candidates) {
			boolean runnable = behavior == this.active ? behavior.shouldContinue(this.npc) : behavior.canStart(this.npc);
			if (runnable) {
				next = behavior;
				break;
			}
		}

		if (next == null) {
			NpcBehavior routine = this.npc.routine.behavior();
			if (routine != null && (routine == this.active ? routine.shouldContinue(this.npc) : routine.canStart(this.npc))) {
				next = routine;
			}
		}

		if (next != this.active) {
			if (this.active != null) {
				this.active.stop(this.npc);
			}

			this.active = next;
			if (this.active != null) {
				this.active.start(this.npc);
			}
		}

		if (this.active != null) {
			this.active.tick(this.npc);
		}
	}

	public ListTag save() {
		ListTag list = new ListTag();
		for (NpcBehavior behavior : this.behaviors) {
			CompoundTag tag = new CompoundTag();
			tag.putString("Id", behavior.getId());
			behavior.save(tag);
			list.addTag(tag);
		}

		return list;
	}

	public void load(ListTag list) {
		this.clear();
		for (Tag<?> entry : list) {
			if (!(entry instanceof CompoundTag tag)) {
				continue;
			}

			String id = tag.getString("Id");
			NpcBehavior behavior = NpcBehaviors.create(id);
			if (behavior == null) {
				Lifeblood.LOGGER.warn("NPC '{}' has unknown behavior '{}', dropping it", this.npc.nickname, id);
				continue;
			}

			behavior.load(tag);
			this.add(behavior);
		}
	}
}
