package lifeblood.building;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.BlockLogicAxisAligned;
import net.minecraft.core.block.BlockLogicBed;
import net.minecraft.core.block.BlockLogicButton;
import net.minecraft.core.block.BlockLogicChest;
import net.minecraft.core.block.BlockLogicDoor;
import net.minecraft.core.block.BlockLogicFenceGate;
import net.minecraft.core.block.BlockLogicFlag;
import net.minecraft.core.block.BlockLogicFullyRotatable;
import net.minecraft.core.block.BlockLogicLadder;
import net.minecraft.core.block.BlockLogicLever;
import net.minecraft.core.block.BlockLogicRail;
import net.minecraft.core.block.BlockLogicRepeater;
import net.minecraft.core.block.BlockLogicRotatable;
import net.minecraft.core.block.BlockLogicSeat;
import net.minecraft.core.block.BlockLogicSign;
import net.minecraft.core.block.BlockLogicSpikes;
import net.minecraft.core.block.BlockLogicStairs;
import net.minecraft.core.block.BlockLogicStatue;
import net.minecraft.core.block.BlockLogicTimer;
import net.minecraft.core.block.BlockLogicTorch;
import net.minecraft.core.block.BlockLogicTrapDoor;
import net.minecraft.core.block.BlockLogicVeryRotatable;
import net.minecraft.core.block.piston.BlockLogicPistonBase;
import net.minecraft.core.block.piston.BlockLogicPistonHead;
import net.minecraft.core.util.helper.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a block's metadata along with a prefab, so stairs, doors, torches, logs and the rest still face the right way.
 *
 * BTA has no rotation helper, and every kind of block stores its facing its own way, so each kind gets a
 * {@link FacingRule} here, matched by its block logic class (most specific first). The encodings were read from BTA's
 * own placement code; see each rule's comment. Blocks without a facing need no rule. Blocks that have a facing this
 * class doesn't know yet are listed in {@link #UNKNOWN_FACING}, so the prefab check can warn about them.
 */
public final class BlockTurns {
	private static final Direction N = Direction.NORTH;
	private static final Direction E = Direction.EAST;
	private static final Direction S = Direction.SOUTH;
	private static final Direction W = Direction.WEST;
	/** No horizontal facing (on the floor or ceiling, say). */
	private static final Direction X = null;

	private record Entry(Class<?> logic, FacingRule rule) {
	}

	/** In order: subclasses before the classes they extend. */
	private static final List<Entry> RULES = new ArrayList<>();
	/** Logic classes with a facing that isn't turned yet (it lives somewhere not worked out, or isn't a plain direction). */
	private static final List<Class<?>> UNKNOWN_FACING = List.of(BlockLogicTimer.class, BlockLogicStatue.class, BlockLogicFlag.class, BlockLogicSeat.class);

	static {
		// Stairs: the low two bits, west/east/north/south; the upside-down bit is kept.
		add(BlockLogicStairs.class, FacingRule.table(3, 0, W, E, N, S));
		// Doors: the low two bits, the side the closed door sits on, clockwise from north. Hinge and open bits are relative.
		add(BlockLogicDoor.class, FacingRule.table(3, 0, N, E, S, W));
		add(BlockLogicTrapDoor.class, FacingRule.table(3, 0, S, N, E, W));
		// Fence gates, repeaters and beds use the player's yaw quarter: south, west, north, east.
		add(BlockLogicFenceGate.class, FacingRule.table(3, 0, S, W, N, E));
		add(BlockLogicRepeater.class, FacingRule.table(3, 0, S, W, N, E));
		add(BlockLogicBed.class, FacingRule.table(3, 0, S, W, N, E));
		// Chests: the low two bits, clockwise from north. The left/right half bits are relative to the facing.
		add(BlockLogicChest.class, FacingRule.table(3, 0, N, E, S, W));
		add(BlockLogicLadder.class, FacingRule.table(7, 0, X, X, N, S, W, E));
		// Torches and buttons: the wall they hang on, 1 to 4; 5 and 6 are floor and ceiling.
		add(BlockLogicTorch.class, FacingRule.table(7, 0, X, W, E, N, S));
		add(BlockLogicButton.class, FacingRule.table(7, 0, X, W, E, N, S));
		add(BlockLogicLever.class, FacingRule.LEVER);
		// Spikes: a full direction id in bits 1 to 3.
		add(BlockLogicSpikes.class, FacingRule.directionId(14, 1));
		add(BlockLogicPistonBase.class, FacingRule.directionId(7, 0));
		add(BlockLogicPistonHead.class, FacingRule.directionId(7, 0));
		// Furnaces, workbenches, dispensers, pumpkins, activators...: a full direction id in the low three bits.
		add(BlockLogicVeryRotatable.class, FacingRule.directionId(7, 0));
		add(BlockLogicFullyRotatable.class, FacingRule.directionId(7, 0));
		add(BlockLogicRotatable.class, FacingRule.directionId(7, 0));
		// Logs and the like: the axis, 0 up, 1 north-south, 2 east-west.
		add(BlockLogicAxisAligned.class, FacingRule.AXIS);
	}

	private BlockTurns() {
	}

	private static void add(Class<?> logic, FacingRule rule) {
		RULES.add(new Entry(logic, rule));
	}

	/** The block's metadata after turning it clockwise this many times. */
	public static int turn(Block<?> block, int meta, int turns) {
		if (Turns.normalize(turns) == 0) {
			return meta;
		}

		FacingRule rule = ruleFor(block.getLogic());
		return rule != null ? rule.turn(meta, Turns.normalize(turns)) : meta;
	}

	/** Whether this block has a facing that won't turn with a prefab yet. */
	public static boolean hasUnknownFacing(Block<?> block) {
		BlockLogic logic = block.getLogic();
		for (Class<?> unknown : UNKNOWN_FACING) {
			if (unknown.isInstance(logic)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static FacingRule ruleFor(BlockLogic logic) {
		if (logic instanceof BlockLogicRail rail) {
			return rail.isPowered ? FacingRule.POWERED_RAIL : FacingRule.RAIL;
		}

		if (logic instanceof BlockLogicSign sign) {
			// Signs on a post turn in sixteenths like the player's yaw; signs on a wall face north/south/west/east.
			return sign.isFreeStanding ? FacingRule.SIXTEENTHS : FacingRule.table(7, 0, X, X, N, S, W, E);
		}

		for (Entry entry : RULES) {
			if (entry.logic().isInstance(logic)) {
				return entry.rule();
			}
		}

		return null;
	}
}
