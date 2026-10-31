package npclaude.ai;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.pathfinder.Path;
import net.minecraft.core.world.pos.TilePos;
import npclaude.entity.EntityNPC;

/**
 * Movement for NPCs. Behaviors only ask to "go there"; how the NPC gets there lives here,
 * so this can later be swapped for a smarter navigator (doors, long trips, getting unstuck)
 * without touching any behavior.
 *
 * For now it uses BTA's built-in pathfinder, which searches roughly 16 blocks at a time.
 */
public class NpcNavigator {
	public static final float DEFAULT_RANGE = 16.0F;

	private final EntityNPC npc;

	public NpcNavigator(EntityNPC npc) {
		this.npc = npc;
	}

	public boolean moveTo(Entity target) {
		Path path = this.npc.world.getPathToEntity(this.npc, target, DEFAULT_RANGE);
		this.npc.setPathToEntity(path);
		return path != null;
	}

	public boolean moveTo(int x, int y, int z) {
		Path path = this.npc.world.getEntityPathToTilePos(this.npc, new TilePos(x, y, z), DEFAULT_RANGE);
		this.npc.setPathToEntity(path);
		return path != null;
	}

	public void stop() {
		this.npc.setPathToEntity(null);
	}

	public boolean isMoving() {
		return this.npc.hasPath();
	}

	/** Teleports to an open spot 2 blocks from the target, like a tamed wolf does. */
	public boolean teleportNear(Entity target) {
		int targetX = MathHelper.floor(target.x);
		int targetY = MathHelper.floor(target.bb.minY);
		int targetZ = MathHelper.floor(target.z);

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					continue;
				}

				int x = targetX + dx;
				int z = targetZ + dz;
				if (this.npc.world.isBlockNormalCube(x, targetY - 1, z)
					&& !this.npc.world.isBlockNormalCube(x, targetY, z)
					&& !this.npc.world.isBlockNormalCube(x, targetY + 1, z)) {
					this.stop();
					this.npc.teleportTo(x + 0.5, targetY, z + 0.5);
					return true;
				}
			}
		}

		return false;
	}
}
