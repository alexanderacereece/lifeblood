package lifeblood.ai.behavior;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;

/** Strolls to random spots within a radius of the NPC's home, pausing between walks. */
public class WanderBehavior implements NpcBehavior {
	public static final String ID = "wander";
	public static final int DEFAULT_RADIUS = 8;
	private static final int MIN_PAUSE = 60;
	private static final int MAX_PAUSE = 200;

	private int radius = DEFAULT_RADIUS;
	private int pause;

	public WanderBehavior() {
	}

	public WanderBehavior(int radius) {
		this.radius = radius;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void start(EntityNPC npc) {
		this.pause = 0;
	}

	@Override
	public void tick(EntityNPC npc) {
		if (npc.navigator.isMoving() || --this.pause > 0) {
			return;
		}

		this.pause = MIN_PAUSE + npc.getRandom().nextInt(MAX_PAUSE - MIN_PAUSE);
		// A few tries, since a random spot may be inside a wall or unreachable.
		for (int attempt = 0; attempt < 5; attempt++) {
			int x = MathHelper.floor(npc.home.getX()) + npc.getRandom().nextInt(this.radius * 2 + 1) - this.radius;
			int y = MathHelper.floor(npc.home.getY()) + npc.getRandom().nextInt(7) - 3;
			int z = MathHelper.floor(npc.home.getZ()) + npc.getRandom().nextInt(this.radius * 2 + 1) - this.radius;
			if (npc.navigator.moveTo(x, y, z)) {
				return;
			}
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putInt("Radius", this.radius);
	}

	@Override
	public void load(CompoundTag tag) {
		this.radius = Math.max(1, tag.getIntegerOrDefault("Radius", DEFAULT_RADIUS));
	}

	@Override
	public String describe() {
		return "wandering (radius " + this.radius + ")";
	}
}
