package npclaude.ai.behavior;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.ai.NpcBehavior;
import npclaude.entity.EntityNPC;

/** Walks to a spot, then stays there (it becomes the NPC's new home). */
public class GoToBehavior implements NpcBehavior {
	public static final String ID = "goto";
	private static final double ARRIVED_DISTANCE_SQR = 1.5 * 1.5;
	private static final int REPLAN_INTERVAL = 40;

	private double x;
	private double y;
	private double z;
	private int replanCooldown;

	public GoToBehavior() {
	}

	public GoToBehavior(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
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
		this.replanCooldown = 0;
	}

	@Override
	public void tick(EntityNPC npc) {
		double dx = npc.x - this.x;
		double dz = npc.z - this.z;
		if (dx * dx + dz * dz <= ARRIVED_DISTANCE_SQR && Math.abs(npc.y - this.y) < 2.0) {
			npc.navigator.stop();
			npc.setHome(this.x, this.y, this.z);
			npc.brain.set(new StayBehavior());
			return;
		}

		if (!npc.navigator.isMoving() || --this.replanCooldown <= 0) {
			this.replanCooldown = REPLAN_INTERVAL;
			npc.navigator.moveTo(MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z));
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putDouble("X", this.x);
		tag.putDouble("Y", this.y);
		tag.putDouble("Z", this.z);
	}

	@Override
	public void load(CompoundTag tag) {
		this.x = tag.getDouble("X");
		this.y = tag.getDouble("Y");
		this.z = tag.getDouble("Z");
	}

	@Override
	public String describe() {
		return String.format("going to %d %d %d", MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z));
	}
}
