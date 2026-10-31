package npclaude.ai;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import npclaude.NPClaude;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;

/**
 * Picks which behavior an NPC runs each tick: the highest-priority one that can run.
 * Switching stops the old behavior and starts the new one.
 */
public class NpcBrain {
	private final EntityNPC npc;
	private final List<NpcBehavior> behaviors = new ArrayList<>();
	@Nullable
	private NpcBehavior active;

	public NpcBrain(EntityNPC npc) {
		this.npc = npc;
	}

	public void add(NpcBehavior behavior) {
		this.behaviors.add(behavior);
		this.behaviors.sort(Comparator.comparingInt(NpcBehavior::getPriority).reversed());
	}

	public void clear() {
		if (this.active != null) {
			this.active.stop(this.npc);
			this.active = null;
		}

		this.behaviors.clear();
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

	@Nullable
	public NpcBehavior getActive() {
		return this.active;
	}

	public void tick() {
		NpcBehavior next = null;
		for (NpcBehavior behavior : this.behaviors) {
			boolean runnable = behavior == this.active ? behavior.shouldContinue(this.npc) : behavior.canStart(this.npc);
			if (runnable) {
				next = behavior;
				break;
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
				NPClaude.LOGGER.warn("NPC '{}' has unknown behavior '{}', dropping it", this.npc.nickname, id);
				continue;
			}

			behavior.load(tag);
			this.add(behavior);
		}
	}
}
