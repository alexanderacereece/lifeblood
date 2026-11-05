package lifeblood.ai;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;

/**
 * The spot an NPC calls home: where "stay" stands, what "wander" circles, where a downed NPC recovers, and where it's
 * brought back to if it falls out of the world. Going somewhere with "go to" makes that spot the new home. Homesteads
 * will tie it to a house.
 */
public final class NpcHome implements NpcPart {
	private final EntityNPC npc;
	private double x;
	private double y;
	private double z;

	public NpcHome(EntityNPC npc) {
		this.npc = npc;
	}

	public void set(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Makes where the NPC stands now its home. */
	public void setHere() {
		this.set(this.npc.x, this.npc.y, this.npc.z);
	}

	public double getX() {
		return this.x;
	}

	public double getY() {
		return this.y;
	}

	public double getZ() {
		return this.z;
	}

	/** How far the NPC is from home, squared (in blocks). */
	public double distanceSqr() {
		double dx = this.npc.x - this.x;
		double dy = this.npc.y - this.y;
		double dz = this.npc.z - this.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Teleports home, for getting out of somewhere deadly. */
	public void teleportHome() {
		this.npc.teleportTo(this.x, this.y, this.z);
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putDouble("HomeX", this.x);
		tag.putDouble("HomeY", this.y);
		tag.putDouble("HomeZ", this.z);
	}

	@Override
	public void load(CompoundTag tag) {
		this.set(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
	}
}
