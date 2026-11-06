package lifeblood.ai;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import org.jetbrains.annotations.Nullable;

/**
 * The spot an NPC calls home: where "stay" stands, what "wander" circles, where a downed NPC recovers, and where it's
 * brought back to if it falls out of the world. Going somewhere with "go to" makes that spot the new home. Someone
 * who lives in a house also has their own bed in it, where they sleep.
 */
public final class NpcHome implements NpcPart {
	private final EntityNPC npc;
	private double x;
	private double y;
	private double z;
	/** Their own bed: the foot block's position and which way it points (0 south, 1 west, 2 north, 3 east); null if none. */
	@Nullable
	private int[] bed;

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

	/** Gives them their own bed: its foot block and which way it points, foot to head (0 south, 1 west, 2 north, 3 east). */
	public void setBed(int x, int y, int z, int facing) {
		this.bed = new int[] {x, y, z, facing};
	}

	/** Their own bed as {x, y, z, facing} of its foot, or null if they have none. */
	@Nullable
	public int[] getBed() {
		return this.bed != null ? this.bed.clone() : null;
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
	public String saveKey() {
		return "Home";
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putDouble("X", this.x);
		tag.putDouble("Y", this.y);
		tag.putDouble("Z", this.z);
		if (this.bed != null) {
			tag.putString("Bed", this.bed[0] + "," + this.bed[1] + "," + this.bed[2] + "," + this.bed[3]);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		this.set(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
		this.bed = null;
		String[] bed = tag.getStringOrDefault("Bed", "").split(",");
		if (bed.length == 4) {
			try {
				this.bed = new int[] {Integer.parseInt(bed[0]), Integer.parseInt(bed[1]), Integer.parseInt(bed[2]), Integer.parseInt(bed[3])};
			} catch (NumberFormatException e) {
				// A damaged bed entry: no bed, rather than a wrong one.
			}
		}
	}
}
