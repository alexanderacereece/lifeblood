package lifeblood.work;

import lifeblood.entity.EntityNPC;

/** How far an NPC can reach to a block, the player's own survival reach, and turning to look at what it works on. */
public final class Reach {
	/** The player's block reach in survival, from the eyes to the block's middle, in blocks. */
	public static final double BLOCKS = 4.5;

	private Reach() {
	}

	/** Whether the middle of this block is within reach of the NPC's eyes. */
	public static boolean canReach(EntityNPC npc, int x, int y, int z) {
		double dx = x + 0.5 - npc.x;
		double dy = y + 0.5 - (npc.y + npc.getHeadHeight());
		double dz = z + 0.5 - npc.z;
		return dx * dx + dy * dy + dz * dz <= BLOCKS * BLOCKS;
	}

	/** Turns the NPC's head and body to look at the middle of a block. */
	public static void lookAt(EntityNPC npc, int x, int y, int z) {
		double dx = x + 0.5 - npc.x;
		double dy = y + 0.5 - (npc.y + npc.getHeadHeight());
		double dz = z + 0.5 - npc.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		npc.yRot = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		npc.xRot = (float) -(Math.atan2(dy, flat) * 180.0 / Math.PI);
		npc.yBodyRot = npc.yRot;
	}
}
