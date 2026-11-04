package lifeblood.building;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogicPortal;
import net.minecraft.core.block.IPainted;
import net.minecraft.core.util.helper.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * What a prefab's materials become where it's placed. Like the gray parts of NPC skins, a prefab is built in
 * placeholders that get filled in:
 * - logs of any tree, and plain (oak) planks and what's made of them, become the local wood ({@link WoodType});
 * - any of the swappable stones becomes the same form in the local stone ({@link StoneFamily});
 * - anything colorable (wool, painted planks, doors, fences, chests, lamps...) that's gray takes the primary color, and
 *   light gray the secondary color. For a building that belongs to a faction, those are the faction's colors.
 * A null part keeps what was built. Every other block, including other colors, is placed exactly as saved.
 */
public record Swaps(@Nullable WoodType wood, @Nullable StoneFamily stone, @Nullable DyeColor primary, @Nullable DyeColor secondary) {
	/** The color placeholder for the primary color. */
	public static final DyeColor PRIMARY_PLACEHOLDER = DyeColor.GRAY;
	/** The color placeholder for the secondary color (BTA calls light gray "silver"). */
	public static final DyeColor SECONDARY_PLACEHOLDER = DyeColor.SILVER;

	public static final Swaps NONE = new Swaps(null, null, null, null);

	public Swaps withColors(@Nullable DyeColor primary, @Nullable DyeColor secondary) {
		return new Swaps(this.wood, this.stone, primary, secondary);
	}

	/** The block and metadata to place for a saved block (already turned). */
	public WoodType.Placed apply(Block<?> block, int meta) {
		// Colors first, on the block as built: plain planks painted by the wood swap below aren't placeholders.
		int colored = this.fillColor(block, meta);
		WoodType.Placed result = new WoodType.Placed(block, colored);
		if (this.wood != null) {
			result = this.wood.swap(result.block(), result.meta());
		}

		if (this.stone != null) {
			result = new WoodType.Placed(this.stone.swap(result.block()), result.meta());
		}

		return result;
	}

	/** The metadata with a placeholder color filled in, if the block has one. */
	private int fillColor(Block<?> block, int meta) {
		DyeColor color = colorOf(block, meta);
		if (color == null) {
			return meta;
		}

		IPainted painted = (IPainted) block.getLogic();
		DyeColor replacement = color == PRIMARY_PLACEHOLDER ? this.primary : color == SECONDARY_PLACEHOLDER ? this.secondary : null;
		return replacement != null ? painted.stripColorFromMetadata(meta) | painted.toMetadata(replacement) : meta;
	}

	/** Whether this block is one swaps can change: a log, plain planks, a swappable stone or a placeholder color. */
	public static boolean isPlaceholder(Block<?> block, int meta) {
		if (WoodType.of(block) != null || WoodType.isPlainPlanks(block) || StoneFamily.of(block) != null) {
			return true;
		}

		DyeColor color = colorOf(block, meta);
		return color == PRIMARY_PLACEHOLDER || color == SECONDARY_PLACEHOLDER;
	}

	/** The paint color of a colored block (wool, painted planks...), or null if it isn't one. Portals aren't paint. */
	@Nullable
	public static DyeColor colorOf(Block<?> block, int meta) {
		if (block.getLogic() instanceof IPainted painted && !(block.getLogic() instanceof BlockLogicPortal)) {
			return painted.fromMetadata(meta);
		}

		return null;
	}
}
