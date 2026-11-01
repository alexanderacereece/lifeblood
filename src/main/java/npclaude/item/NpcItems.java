package npclaude.item;

import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import npclaude.NPClaude;
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
	private static final int GUARDIAN_AMULET_ID = 25000;
	private static final int WORD_BOOK_ID = 25001;

	public static Item guardianAmulet;
	public static Item wordBook;

	private NpcItems() {
	}

	/** Hooks item and recipe creation into the game's startup. */
	public static void init() {
		CommonEvents.AFTER_ITEM_INIT.listen(Key.of(NPClaude.MOD_ID), NpcItems::registerItems);
		CommonEvents.RECIPES_NAMESPACE_INIT.listen(Key.of(NPClaude.MOD_ID), () -> RecipeBuilder.initNameSpace(NPClaude.MOD_ID));
		CommonEvents.RECIPES_READY.listen(Key.of(NPClaude.MOD_ID), NpcItems::registerRecipes);
	}

	private static void registerItems() {
		// The builder adds the mod id to the translation key itself: item.npclaude.guardian_amulet.
		guardianAmulet = new ItemBuilder(NPClaude.MOD_ID).setStackSize(1)
			.setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
			.build(new Item("guardian_amulet", NPClaude.MOD_ID + ":item/guardian_amulet", GUARDIAN_AMULET_ID));
		wordBook = new ItemBuilder(NPClaude.MOD_ID).setStackSize(1)
			.setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
			.build(new ItemWordBook("word_book", NPClaude.MOD_ID + ":item/word_book", WORD_BOOK_ID));
		NPClaude.LOGGER.info("Registered the guardian amulet and the word book.");
	}

	/** A block of gold set in gold, on a string. */
	private static void registerRecipes() {
		RecipeBuilder.Shaped(NPClaude.MOD_ID)
			.setShape(" S ", "GBG", " G ")
			.addInput('S', Items.STRING)
			.addInput('G', Items.INGOT_GOLD)
			.addInput('B', Blocks.BLOCK_GOLD)
			.create("guardian_amulet", new ItemStack(guardianAmulet));
		// A book and a feather to write with.
		RecipeBuilder.Shapeless(NPClaude.MOD_ID)
			.addInput(Items.BOOK)
			.addInput(Items.FEATHER_CHICKEN)
			.create("word_book", new ItemStack(wordBook));
	}

	public static boolean isGuardianAmulet(ItemStack stack) {
		return stack != null && guardianAmulet != null && stack.getItem() == guardianAmulet;
	}
}
