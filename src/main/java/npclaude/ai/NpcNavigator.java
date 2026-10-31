package npclaude.ai;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pathfinder.Node;
import net.minecraft.core.world.pathfinder.Path;
import net.minecraft.core.world.pos.TilePos;
import npclaude.entity.EntityNPC;
import npclaude.mixin.PathAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Movement for NPCs. Behaviors only ask to "go there"; how the NPC gets there lives here,
 * so this can later be swapped for a smarter navigator (doors, long trips) without touching any behavior.
 *
 * It uses BTA's built-in pathfinder (about 16 blocks per search) and adds what that lacks:
 * paths through or beside hazards (cactus, fire, lava...) are refused, a stuck NPC jumps and then gives up the path
 * so its behavior can pick another, and it can run straight in a direction when no path can be found.
 */
public class NpcNavigator {
	public static final float DEFAULT_RANGE = 16.0F;
	/** Barely moving for this many ticks while following a path counts as stuck: try jumping. */
	private static final int STUCK_JUMP_TICKS = 8;
	/** Stuck this long: drop the path so the behavior finds another. */
	private static final int STUCK_GIVE_UP_TICKS = 30;
	private static final double STUCK_MOVE_SQR = 0.03 * 0.03;

	private final EntityNPC npc;
	private int stuckTicks;
	private double lastX;
	private double lastZ;
	/** Direction of a straight run (radians), and ticks left on it; 0 when not running straight. */
	private double directAngle;
	private int directTicks;

	public NpcNavigator(EntityNPC npc) {
		this.npc = npc;
	}

	public boolean moveTo(Entity target) {
		return this.follow(this.npc.world.getPathToEntity(this.npc, target, DEFAULT_RANGE));
	}

	public boolean moveTo(int x, int y, int z) {
		return this.follow(this.npc.world.getEntityPathToTilePos(this.npc, new TilePos(x, y, z), DEFAULT_RANGE));
	}

	private boolean follow(@Nullable Path path) {
		if (path == null || isHazardous(this.npc.world, path)) {
			this.npc.setPathToEntity(null);
			return false;
		}

		this.npc.setPathToEntity(path);
		this.directTicks = 0;
		this.stuckTicks = 0;
		return true;
	}

	/** Runs straight along a heading (radians) for a while, for when no path can be found. Stops at hazards. */
	public void runDirect(double angle, int ticks) {
		this.npc.setPathToEntity(null);
		this.directAngle = angle;
		this.directTicks = ticks;
		this.stuckTicks = 0;
	}

	public void stop() {
		this.npc.setPathToEntity(null);
		this.directTicks = 0;
	}

	public boolean isMoving() {
		return this.npc.hasPath() || this.directTicks > 0;
	}

	/** Called every AI tick, after the vanilla path-following has steered the NPC. */
	public void tick() {
		if (this.directTicks > 0) {
			this.tickDirectRun();
		}

		if (!this.isMoving()) {
			this.stuckTicks = 0;
			return;
		}

		double dx = this.npc.x - this.lastX;
		double dz = this.npc.z - this.lastZ;
		this.lastX = this.npc.x;
		this.lastZ = this.npc.z;
		if (dx * dx + dz * dz > STUCK_MOVE_SQR) {
			this.stuckTicks = 0;
			return;
		}

		this.stuckTicks++;
		if (this.stuckTicks >= STUCK_GIVE_UP_TICKS) {
			this.stop();
			this.stuckTicks = 0;
		} else if (this.stuckTicks >= STUCK_JUMP_TICKS && this.npc.horizontalCollision) {
			// Usually caught on a block edge; a hop gets over or around it.
			this.npc.setJumping(true);
		}
	}

	private void tickDirectRun() {
		this.directTicks--;
		double aheadX = this.npc.x + Math.cos(this.directAngle);
		double aheadZ = this.npc.z + Math.sin(this.directAngle);
		if (isHazardAt(this.npc.world, MathHelper.floor(aheadX), MathHelper.floor(this.npc.y), MathHelper.floor(aheadZ))) {
			this.directTicks = 0;
			return;
		}

		this.npc.steerToward(this.npc.x + Math.cos(this.directAngle) * 4.0, this.npc.z + Math.sin(this.directAngle) * 4.0);
		if (this.npc.horizontalCollision) {
			this.npc.setJumping(true);
		}
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
				if (this.npc.world.isBlockNormalCube(new TilePos(x, targetY - 1, z))
					&& !this.npc.world.isBlockNormalCube(new TilePos(x, targetY, z))
					&& !this.npc.world.isBlockNormalCube(new TilePos(x, targetY + 1, z))
					&& !isHazardAt(this.npc.world, x, targetY, z)) {
					this.stop();
					this.npc.teleportTo(x + 0.5, targetY, z + 0.5);
					return true;
				}
			}
		}

		return false;
	}

	/** A path is hazardous if any step stands in, or right beside, something that hurts. */
	private static boolean isHazardous(World world, Path path) {
		for (Node node : ((PathAccessor) path).npclaude$getNodes()) {
			if (isHazardAt(world, node.x, node.y, node.z)) {
				return true;
			}
		}

		return false;
	}

	/** Hurts to stand here: a hazard at feet or head height, magma underfoot, or a cactus right beside the feet. */
	public static boolean isHazardAt(World world, int x, int y, int z) {
		if (isHazardBlock(blockAt(world, x, y, z)) || isHazardBlock(blockAt(world, x, y + 1, z)) || blockAt(world, x, y - 1, z) == Blocks.MAGMA) {
			return true;
		}

		return blockAt(world, x + 1, y, z) == Blocks.CACTUS || blockAt(world, x - 1, y, z) == Blocks.CACTUS
			|| blockAt(world, x, y, z + 1) == Blocks.CACTUS || blockAt(world, x, y, z - 1) == Blocks.CACTUS;
	}

	private static Block<?> blockAt(World world, int x, int y, int z) {
		return world.getBlockType(new TilePos(x, y, z));
	}

	private static boolean isHazardBlock(@Nullable Block<?> block) {
		return block != null && (block == Blocks.CACTUS || block == Blocks.FIRE || block == Blocks.MAGMA || block == Blocks.COBWEB
			|| block == Blocks.FLUID_LAVA_FLOWING || block == Blocks.FLUID_LAVA_STILL);
	}
}
