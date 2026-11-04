package lifeblood.building;

import java.util.Locale;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * The kinds of stone that differ from place to place, each with the same set of cut and built forms. A prefab built
 * from one stone is placed in another by swapping each block for the same form in the local stone: granite bricks
 * become basalt bricks, a cobbled granite stair a cobbled basalt stair. Forms a stone doesn't have stay as built.
 *
 * Sandstone and marble aren't here on purpose: they're chosen materials, not whatever is underground.
 */
public enum StoneFamily {
	STONE(Blocks.STONE, Blocks.COBBLE_STONE, Blocks.COBBLE_STONE_MOSSY, Blocks.MOSS_STONE, Blocks.STONE_CARVED, Blocks.STONE_POLISHED,
		Blocks.BRICK_STONE, Blocks.SLAB_COBBLE_STONE, Blocks.SLAB_BRICK_STONE, Blocks.SLAB_STONE_POLISHED, Blocks.STAIRS_COBBLE_STONE,
		Blocks.STAIRS_BRICK_STONE, Blocks.BUTTON_STONE, Blocks.PRESSURE_PLATE_STONE, Blocks.PRESSURE_PLATE_COBBLE_STONE),
	BASALT(Blocks.BASALT, Blocks.COBBLE_BASALT, Blocks.COBBLE_BASALT_MOSSY, Blocks.MOSS_BASALT, Blocks.BASALT_CARVED, Blocks.BASALT_POLISHED,
		Blocks.BRICK_BASALT, Blocks.SLAB_COBBLE_BASALT, Blocks.SLAB_BRICK_BASALT, Blocks.SLAB_BASALT_POLISHED, Blocks.STAIRS_COBBLE_BASALT,
		Blocks.STAIRS_BRICK_BASALT, Blocks.BUTTON_BASALT, Blocks.PRESSURE_PLATE_BASALT, Blocks.PRESSURE_PLATE_COBBLE_BASALT),
	LIMESTONE(Blocks.LIMESTONE, Blocks.COBBLE_LIMESTONE, Blocks.COBBLE_LIMESTONE_MOSSY, Blocks.MOSS_LIMESTONE, Blocks.LIMESTONE_CARVED,
		Blocks.LIMESTONE_POLISHED, Blocks.BRICK_LIMESTONE, Blocks.SLAB_COBBLE_LIMESTONE, Blocks.SLAB_BRICK_LIMESTONE, Blocks.SLAB_LIMESTONE_POLISHED,
		Blocks.STAIRS_COBBLE_LIMESTONE, Blocks.STAIRS_BRICK_LIMESTONE, Blocks.BUTTON_LIMESTONE, Blocks.PRESSURE_PLATE_LIMESTONE,
		Blocks.PRESSURE_PLATE_COBBLE_LIMESTONE),
	GRANITE(Blocks.GRANITE, Blocks.COBBLE_GRANITE, Blocks.COBBLE_GRANITE_MOSSY, Blocks.MOSS_GRANITE, Blocks.GRANITE_CARVED, Blocks.GRANITE_POLISHED,
		Blocks.BRICK_GRANITE, Blocks.SLAB_COBBLE_GRANITE, Blocks.SLAB_BRICK_GRANITE, Blocks.SLAB_GRANITE_POLISHED, Blocks.STAIRS_COBBLE_GRANITE,
		Blocks.STAIRS_BRICK_GRANITE, Blocks.BUTTON_GRANITE, Blocks.PRESSURE_PLATE_GRANITE, Blocks.PRESSURE_PLATE_COBBLE_GRANITE),
	PERMAFROST(Blocks.PERMAFROST, Blocks.COBBLE_PERMAFROST, null, null, Blocks.PERMAFROST_CARVED, Blocks.PERMAFROST_POLISHED,
		Blocks.BRICK_PERMAFROST, Blocks.SLAB_COBBLE_PERMAFROST, Blocks.SLAB_BRICK_PERMAFROST, Blocks.SLAB_PERMAFROST_POLISHED,
		Blocks.STAIRS_COBBLE_PERMAFROST, Blocks.STAIRS_BRICK_PERMAFROST, Blocks.BUTTON_PERMAFROST, Blocks.PRESSURE_PLATE_PERMAFROST,
		Blocks.PRESSURE_PLATE_COBBLE_PERMAFROST),
	SLATE(Blocks.SLATE, null, null, null, Blocks.SLATE_CARVED, Blocks.SLATE_POLISHED, Blocks.BRICK_SLATE, null, Blocks.SLAB_BRICK_SLATE,
		Blocks.SLAB_SLATE_POLISHED, null, Blocks.STAIRS_BRICK_SLATE, Blocks.BUTTON_SLATE, Blocks.PRESSURE_PLATE_SLATE, null),
	NETHERRACK(Blocks.NETHERRACK, Blocks.COBBLE_NETHERRACK, null, null, Blocks.NETHERRACK_CARVED, Blocks.NETHERRACK_POLISHED,
		Blocks.BRICK_NETHERRACK, Blocks.SLAB_COBBLE_NETHERRACK, Blocks.SLAB_BRICK_NETHERRACK, Blocks.SLAB_NETHERRACK_POLISHED,
		Blocks.STAIRS_COBBLE_NETHERRACK, Blocks.STAIRS_BRICK_NETHERRACK, Blocks.BUTTON_NETHERRACK, Blocks.PRESSURE_PLATE_NETHERRACK,
		Blocks.PRESSURE_PLATE_COBBLE_NETHERRACK),
	GLOOMSTONE(Blocks.GLOOMSTONE, Blocks.COBBLE_GLOOMSTONE, null, null, Blocks.GLOOMSTONE_CARVED, Blocks.GLOOMSTONE_POLISHED,
		Blocks.BRICK_GLOOMSTONE, Blocks.SLAB_COBBLE_GLOOMSTONE, Blocks.SLAB_BRICK_GLOOMSTONE, Blocks.SLAB_GLOOMSTONE_POLISHED,
		Blocks.STAIRS_COBBLE_GLOOMSTONE, Blocks.STAIRS_BRICK_GLOOMSTONE, Blocks.BUTTON_GLOOMSTONE, Blocks.PRESSURE_PLATE_GLOOMSTONE,
		Blocks.PRESSURE_PLATE_COBBLE_GLOOMSTONE);

	/**
	 * Each stone's blocks, by form, in this order: the natural stone, cobbled, mossy cobbled, mossy stone, carved,
	 * polished, bricks, then slabs (cobbled, brick, polished), stairs (cobbled, brick), button, pressure plates
	 * (stone, cobbled). Null where the stone doesn't come in that form.
	 */
	private final Block<?>[] forms;

	StoneFamily(Block<?>... forms) {
		this.forms = forms;
	}

	/** The natural, uncut stone, as found underground. */
	public Block<?> natural() {
		return this.forms[0];
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static StoneFamily byId(String id) {
		for (StoneFamily family : values()) {
			if (family.id().equals(id)) {
				return family;
			}
		}

		return null;
	}

	/** The stone a block is made of, or null if it isn't one of these. */
	@Nullable
	public static StoneFamily of(Block<?> block) {
		for (StoneFamily family : values()) {
			if (family.formOf(block) >= 0) {
				return family;
			}
		}

		return null;
	}

	/** The same form of block in this stone, or the block itself if it isn't a stone form or this stone lacks the form. */
	public Block<?> swap(Block<?> block) {
		StoneFamily from = of(block);
		if (from == null || from == this) {
			return block;
		}

		Block<?> swapped = this.forms[from.formOf(block)];
		return swapped != null ? swapped : block;
	}

	private int formOf(Block<?> block) {
		for (int i = 0; i < this.forms.length; i++) {
			if (this.forms[i] == block) {
				return i;
			}
		}

		return -1;
	}
}
