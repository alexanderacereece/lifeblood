package lifeblood.homestead.life;

import net.minecraft.core.block.Blocks;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.work.Approach;
import org.jetbrains.annotations.Nullable;

/**
 * Bedtime: walks to their own bed and sleeps in it, lying on their back with their head on the pillow, until morning.
 * Someone without a bed (or whose bed is gone) stays close to home instead. Anything that interrupts (a fight, being
 * hit) wakes them, as it would a player.
 */
public final class SleepBehavior implements NpcBehavior {
	public static final String ID = "sleep";
	/** How high a bed's top is above its block, where a sleeper lies. */
	private static final double BED_TOP = 0.5625;

	private final Approach approach = new Approach();
	private boolean asleep;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void tick(EntityNPC npc) {
		int[] bed = npc.home.getBed();
		if (bed == null || npc.world.getBlockType(new TilePos(bed[0], bed[1], bed[2])) != Blocks.BED) {
			this.wake(npc);
			if (!npc.navigator.isMoving() && npc.home.distanceSqr() > 9.0) {
				npc.navigator.moveTo((int) Math.floor(npc.home.getX()), (int) Math.floor(npc.home.getY()), (int) Math.floor(npc.home.getZ()));
			}

			return;
		}

		if (!this.asleep && this.approach.toward(npc, bed[0], bed[1], bed[2]) != Approach.Status.REACHED) {
			return;
		}

		this.asleep = true;
		npc.setSleeping(true);
		// Lying from foot to head, the feet toward the foot of the bed: it faces the way the bed points, backwards.
		int[] head = headOf(bed);
		double x = (bed[0] + head[0]) / 2.0 + 0.5;
		double z = (bed[2] + head[2]) / 2.0 + 0.5;
		float yaw = ((bed[3] + 2) % 4) * 90.0F;
		npc.setPos(x, bed[1] + BED_TOP, z);
		npc.xd = 0.0;
		npc.zd = 0.0;
		npc.yRot = yaw;
		npc.yBodyRot = yaw;
	}

	@Override
	public void stop(EntityNPC npc) {
		this.wake(npc);
		npc.navigator.stop();
	}

	/** Gets up, standing on the floor beside the bed: standing on a bed, a low ceiling can leave no room to move. */
	private void wake(EntityNPC npc) {
		if (this.asleep) {
			this.asleep = false;
			npc.setSleeping(false);
			int[] bed = npc.home.getBed();
			double[] spot = bed != null ? besideBed(npc.world, bed) : null;
			if (spot != null) {
				npc.setPos(spot[0], spot[1], spot[2]);
			}
		}
	}

	/**
	 * A spot on the floor beside a bed ({x, y, z} of the feet, centered), with room to stand: beside the foot first, then
	 * the head. Null if the bed is walled in all round.
	 */
	@Nullable
	public static double[] besideBed(World world, int[] bed) {
		int[] head = headOf(bed);
		int[][] halves = {{bed[0], bed[1], bed[2]}, head};
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		TilePos pos = new TilePos();
		for (int[] half : halves) {
			for (int[] side : sides) {
				int x = half[0] + side[0];
				int z = half[2] + side[1];
				boolean floor = world.getBlockType(pos.set(x, half[1] - 1, z)).getMaterial().blocksMotion();
				boolean feet = !world.getBlockType(pos.set(x, half[1], z)).getMaterial().blocksMotion();
				boolean headroom = !world.getBlockType(pos.set(x, half[1] + 1, z)).getMaterial().blocksMotion();
				if (floor && feet && headroom && world.getBlockType(pos.set(x, half[1], z)) != Blocks.BED) {
					return new double[] {x + 0.5, half[1], z + 0.5};
				}
			}
		}

		return null;
	}

	/** The head block of a bed, one step from its foot the way it points (south, west, north, east). */
	private static int[] headOf(int[] bed) {
		int[] stepX = {0, -1, 0, 1};
		int[] stepZ = {1, 0, -1, 0};
		return new int[] {bed[0] + stepX[bed[3]], bed[1], bed[2] + stepZ[bed[3]]};
	}

	@Override
	public String describe() {
		return this.asleep ? "asleep in bed" : "going to bed";
	}
}
