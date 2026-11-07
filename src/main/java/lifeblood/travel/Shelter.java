package lifeblood.travel;

import java.util.function.Predicate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Material;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.ownership.BlockOwners;
import lifeblood.work.BlockBreaking;
import lifeblood.work.BlockUse;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Shelter for a night away from home, as a player makes one: dig two blocks down where they stand, drop in, and put a
 * block over their head, from what they just dug. In the morning they open it up and cut a step to climb out. Where
 * the ground won't do (water, sand over a cave, bedrock-hard stone), they try a few steps along.
 */
public final class Shelter {
	/** How many spots they try before settling for the open ground. */
	private static final int TRIES = 6;

	private enum Step {
		CHOOSE, DIG_FEET, DIG_BELOW, COVER, ASLEEP, OPEN, STEP_OUT, DONE
	}

	private final EntityNPC npc;
	private final BlockBreaking breaking;
	private Step step = Step.CHOOSE;
	private int holeX;
	private int holeY;
	private int holeZ;
	private int tries;

	public Shelter(EntityNPC npc) {
		this.npc = npc;
		this.breaking = new BlockBreaking(npc);
	}

	/** Blocks that do to close a shelter: any solid block they carry. */
	private static final Predicate<ItemStack> COVER = stack -> stack.getItem() instanceof ItemBlock<?> block
		&& block.getBlock().getMaterial().blocksMotion() && !block.getBlock().getMaterial().isLiquid();

	/** One tick of the night: getting into shelter and staying there. Call until morning, then morning() until done. */
	public void night() {
		World world = this.npc.world;
		ItemPickup.pickUpNearby(this.npc);
		switch (this.step) {
			case CHOOSE -> {
				int x = (int) Math.floor(this.npc.x);
				int y = (int) Math.floor(this.npc.y);
				int z = (int) Math.floor(this.npc.z);
				if (diggable(world, x, y, z)) {
					this.holeX = x;
					this.holeY = y;
					this.holeZ = z;
					this.step = Step.DIG_FEET;
				} else if (++this.tries > TRIES) {
					// Nowhere to dig: they sleep rough where they are.
					this.step = Step.ASLEEP;
				} else if (!this.npc.navigator.isMoving()) {
					this.npc.navigator.moveTo(x + this.npc.getRandom().nextInt(7) - 3, y, z + this.npc.getRandom().nextInt(7) - 3);
				}
			}
			case DIG_FEET -> this.dig(this.holeY - 1, Step.DIG_BELOW);
			case DIG_BELOW -> this.dig(this.holeY - 2, Step.COVER);
			case COVER -> {
				// Once they've dropped in, the block over their head closes it.
				if (this.npc.y > this.holeY - 1.5) {
					this.npc.navigator.moveTo(this.holeX, this.holeY - 2, this.holeZ);
					return;
				}

				if (BlockUse.holdItem(this.npc, COVER)) {
					BlockUse.placeAt(this.npc, this.holeX, this.holeY, this.holeZ);
				}

				this.step = Step.ASLEEP;
			}
			case ASLEEP, OPEN, STEP_OUT, DONE -> this.npc.navigator.stop();
		}
	}

	/** One tick of the morning: opening the shelter and climbing out. Returns true once they're out. */
	public boolean morning() {
		World world = this.npc.world;
		if (this.step == Step.ASLEEP && this.npc.y > this.holeY - 1.5) {
			// They slept on the open ground.
			this.step = Step.DONE;
		}

		switch (this.step) {
			case CHOOSE, DIG_FEET, DIG_BELOW, COVER -> this.step = Step.DONE;
			case ASLEEP -> this.step = Step.OPEN;
			case OPEN -> {
				if (world.getBlockType(new TilePos(this.holeX, this.holeY, this.holeZ)) == Blocks.AIR
					|| this.breaking.dig(this.holeX, this.holeY, this.holeZ) != BlockBreaking.Result.DIGGING) {
					this.step = Step.STEP_OUT;
				}
			}
			case STEP_OUT -> {
				// A step cut into the side of the hole, one block up, to climb out by.
				int stepX = this.holeX + 1;
				TilePos step = new TilePos(stepX, this.holeY - 1, this.holeZ);
				if (world.getBlockType(step).getMaterial().blocksMotion()) {
					this.breaking.dig(stepX, this.holeY - 1, this.holeZ);
					return false;
				}

				if (world.getBlockType(step.set(stepX, this.holeY, this.holeZ)).getMaterial().blocksMotion()) {
					this.breaking.dig(stepX, this.holeY, this.holeZ);
					return false;
				}

				if (this.npc.y < this.holeY - 0.5) {
					if (!this.npc.navigator.isMoving()) {
						this.npc.navigator.moveTo(stepX + 1, this.holeY, this.holeZ);
					}

					return false;
				}

				this.step = Step.DONE;
			}
			default -> {
			}
		}

		return this.step == Step.DONE;
	}

	private void dig(int y, Step next) {
		BlockBreaking.Result result = this.breaking.dig(this.holeX, y, this.holeZ);
		if (result != BlockBreaking.Result.DIGGING) {
			this.step = result == BlockBreaking.Result.BROKEN || this.npc.world.getBlockType(new TilePos(this.holeX, y, this.holeZ)) == Blocks.AIR
				? next : Step.ASLEEP;
		}
	}

	/**
	 * Two blocks of plain ground under the feet, solid beneath them, with no water, lava or falling sand to come in, and
	 * nobody's: the hole and the step cut to climb out by are only dug where no one placed the blocks, so a traveler never
	 * digs into someone's floor.
	 */
	private static boolean diggable(World world, int x, int y, int z) {
		TilePos pos = new TilePos();
		for (int depth = 1; depth <= 3; depth++) {
			Block<?> block = world.getBlockType(pos.set(x, y - depth, z));
			Material material = block.getMaterial();
			if (!material.blocksMotion() || block.getHardness() < 0.0F || block.getHardness() > 3.0F || isLoose(block)
				|| BlockOwners.ownerAt(world, x, y - depth, z) != null) {
				return false;
			}
		}

		// The step out, cut into the side of the hole (see morning()).
		if (BlockOwners.ownerAt(world, x + 1, y - 1, z) != null || BlockOwners.ownerAt(world, x + 1, y, z) != null) {
			return false;
		}

		// Nothing wet or burning beside the hole.
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] side : sides) {
			for (int depth = 1; depth <= 2; depth++) {
				if (world.getBlockType(pos.set(x + side[0], y - depth, z + side[1])).getMaterial().isLiquid()) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean isLoose(Block<?> block) {
		return block == Blocks.SAND || block == Blocks.GRAVEL;
	}

	/** Whether they're tucked in for the night. */
	public boolean isAsleep() {
		return this.step == Step.ASLEEP;
	}

	@Nullable
	public String describe() {
		return this.step == Step.ASLEEP ? "sheltering for the night" : this.step == Step.CHOOSE ? "looking for shelter" : "digging in for the night";
	}
}
