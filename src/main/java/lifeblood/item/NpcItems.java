package lifeblood.item;

import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import lifeblood.Lifeblood;
import lifeblood.NpcConfig;
import turniplabs.halplibe.helper.ItemBuilder;
import turniplabs.halplibe.helper.RecipeBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * The mod's items. The guardian amulet: give it to an NPC and it can't be killed while it carries it (it's knocked
 * down instead, like an invincible NPC). Expensive on purpose, so it goes to the people who matter.
 */
public final class NpcItems {
	public static Item guardianAmulet;
	public static Item wordBook;

	private NpcItems() {
	}

	/** Hooks item and recipe creation into the game's startup. */
	public static void init() {
		CommonEvents.AFTER_ITEM_INIT.listen(Key.of(Lifeblood.MOD_ID), NpcItems::registerItems);
		CommonEvents.RECIPES_NAMESPACE_INIT.listen(Key.of(Lifeblood.MOD_ID), () -> RecipeBuilder.initNameSpace(Lifeblood.MOD_ID));
		CommonEvents.RECIPES_READY.listen(Key.of(Lifeblood.MOD_ID), NpcItems::registerRecipes);
	}

	private static void registerItems() {
		// The builder adds the mod id to the translation key itself: item.lifeblood.guardian_amulet.
		guardianAmulet = new ItemBuilder(Lifeblood.MOD_ID).setStackSize(1)
			.setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
			.build(new Item("guardian_amulet", Lifeblood.MOD_ID + ":item/guardian_amulet", NpcConfig.guardianAmuletId()));
		wordBook = new ItemBuilder(Lifeblood.MOD_ID).setStackSize(1)
			.setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
			.build(new ItemWordBook("word_book", Lifeblood.MOD_ID + ":item/word_book", NpcConfig.wordBookId()));
		Lifeblood.LOGGER.info("Registered the guardian amulet and the word book.");
	}

	/** A block of gold set in gold, on a string. */
	private static void registerRecipes() {
		RecipeBuilder.Shaped(Lifeblood.MOD_ID)
			.setShape(" S ", "GBG", " G ")
			.addInput('S', Items.STRING)
			.addInput('G', Items.INGOT_GOLD)
			.addInput('B', Blocks.BLOCK_GOLD)
			.create("guardian_amulet", new ItemStack(guardianAmulet));
		// A book and a feather to write with.
		RecipeBuilder.Shapeless(Lifeblood.MOD_ID)
			.addInput(Items.BOOK)
			.addInput(Items.FEATHER_CHICKEN)
			.create("word_book", new ItemStack(wordBook));
	}

	public static boolean isGuardianAmulet(ItemStack stack) {
		return stack != null && guardianAmulet != null && stack.getItem() == guardianAmulet;
	}
}
