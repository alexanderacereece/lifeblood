package lifeblood.building;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.IPainted;
import net.minecraft.core.util.helper.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * The kinds of wood that grow in different places, and the planks each makes. In BTA every log gives planks of the
 * same block, colored by the tree, as if dyed: oak gives plain planks, birch white, pine brown, and so on (from the
 * game's crafting recipes). So where a prefab is placed:
 * - logs of any of these trees become the local kind;
 * - plain (oak) planks and everything made from them, like stairs, slabs, fences, doors and chests, become the local
 *   wood's planks: plain for oak, painted the wood's color for the rest.
 * Mossy and scorched logs aren't kinds of tree, so they stay as built, and so do planks already painted a color.
 */
public enum WoodType {
	OAK(Blocks.LOG_OAK, null),
	PINE(Blocks.LOG_PINE, DyeColor.BROWN),
	BIRCH(Blocks.LOG_BIRCH, DyeColor.WHITE),
	CHERRY(Blocks.LOG_CHERRY, DyeColor.PINK),
	EUCALYPTUS(Blocks.LOG_EUCALYPTUS, DyeColor.ORANGE),
	THORN(Blocks.LOG_THORN, DyeColor.RED),
	PALM(Blocks.LOG_PALM, DyeColor.YELLOW);

	/** Each plain plank block and the painted block it becomes when colored, as BTA's own painting does. */
	private static final Map<Block<?>, Block<?>> PAINTED_FORM = Map.ofEntries(
		Map.entry(Blocks.PLANKS_OAK, Blocks.PLANKS_OAK_PAINTED),
		Map.entry(Blocks.SLAB_PLANKS_OAK, Blocks.SLAB_PLANKS_PAINTED),
		Map.entry(Blocks.STAIRS_PLANKS_OAK, Blocks.STAIRS_PLANKS_PAINTED),
		Map.entry(Blocks.FENCE_PLANKS_OAK, Blocks.FENCE_PLANKS_OAK_PAINTED),
		Map.entry(Blocks.FENCE_GATE_PLANKS_OAK, Blocks.FENCE_GATE_PLANKS_OAK_PAINTED),
		Map.entry(Blocks.DOOR_PLANKS_OAK_BOTTOM, Blocks.DOOR_PLANKS_PAINTED_BOTTOM),
		Map.entry(Blocks.DOOR_PLANKS_OAK_TOP, Blocks.DOOR_PLANKS_PAINTED_TOP),
		Map.entry(Blocks.TRAPDOOR_PLANKS_OAK, Blocks.TRAPDOOR_PLANKS_PAINTED),
		Map.entry(Blocks.CHEST_PLANKS_OAK, Blocks.CHEST_PLANKS_OAK_PAINTED),
		Map.entry(Blocks.SIGN_POST_PLANKS_OAK, Blocks.SIGN_POST_PLANKS_OAK_PAINTED),
		Map.entry(Blocks.SIGN_WALL_PLANKS_OAK, Blocks.SIGN_WALL_PLANKS_OAK_PAINTED),
		Map.entry(Blocks.BUTTON_PLANKS_OAK, Blocks.BUTTON_PLANKS_PAINTED),
		Map.entry(Blocks.PRESSURE_PLATE_PLANKS_OAK, Blocks.PRESSURE_PLATE_PLANKS_OAK_PAINTED));

	public final Block<?> log;
	/** The color this wood's planks come out, or null for plain planks (oak). */
	@Nullable
	public final DyeColor plankColor;

	WoodType(Block<?> log, @Nullable DyeColor plankColor) {
		this.log = log;
		this.plankColor = plankColor;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static WoodType byId(String id) {
		for (WoodType type : values()) {
			if (type.id().equals(id)) {
				return type;
			}
		}

		return null;
	}

	/** The wood a log is, or null if the block isn't one of these logs. */
	@Nullable
	public static WoodType of(Block<?> block) {
		for (WoodType type : values()) {
			if (type.log == block) {
				return type;
			}
		}

		return null;
	}

	/** Whether the block is plain planks or made from them, so it takes the local wood's planks. */
	public static boolean isPlainPlanks(Block<?> block) {
		return PAINTED_FORM.containsKey(block);
	}

	/** A block and its metadata. */
	public record Placed(Block<?> block, int meta) {
	}

	/** This wood's version of a block: its log for another tree's, its planks for plain planks; anything else as it is. */
	public Placed swap(Block<?> block, int meta) {
		if (of(block) != null) {
			return new Placed(this.log, meta);
		}

		Block<?> painted = PAINTED_FORM.get(block);
		if (painted == null || this.plankColor == null) {
			return new Placed(block, meta);
		}

		// Painting keeps the block's other data (facing, halves) and adds the color, as BTA's paint brush does.
		IPainted logic = (IPainted) painted.getLogic();
		return new Placed(painted, logic.stripColorFromMetadata(meta) | logic.toMetadata(this.plankColor));
	}
}
