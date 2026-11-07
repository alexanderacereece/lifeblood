package lifeblood.work;

import net.minecraft.core.data.registry.Registries;
import net.minecraft.core.data.registry.recipe.entry.RecipeEntryFurnace;
import net.minecraft.core.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a furnace makes of something, by the game's own furnace recipes (modded ones included), the same lookup the
 * furnace itself does: raw pork to cooked, dough to bread, ore to ingots.
 */
public final class Smelting {
	/** The furnace's input, fuel and output slots. */
	public static final int INPUT_SLOT = 0;
	public static final int FUEL_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;

	private Smelting() {
	}

	/** What smelting this gives, or null if a furnace won't take it. */
	@Nullable
	public static ItemStack resultOf(ItemStack input) {
		for (RecipeEntryFurnace recipe : Registries.RECIPES.getAllFurnaceRecipes()) {
			if (recipe != null && recipe.matches(input)) {
				return recipe.getOutput();
			}
		}

		return null;
	}
}
