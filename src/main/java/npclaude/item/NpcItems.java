package npclaude.item;

import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import npclaude.NPClaude;
import turniplabs.halplibe.helper.ItemBuilder;
import turniplabs.halplibe.helper.RecipeBuilder;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * The mod's items. The guardian amulet: give it to an NPC and it can't be killed while it carries it (it's knocked
 * down instead, like an invincible NPC). Expensive on purpose, so it goes to the people who matter.
 */
public final class NpcItems {
	private static final int GUARDIAN_AMULET_ID = 25000;

	public static Item guardianAmulet;

	private NpcItems() {
	}

	/** Hooks item and recipe creation into the game's startup. */
	public static void init() {
		CommonEvents.AFTER_ITEM_INIT.listen(Key.of(NPClaude.MOD_ID), NpcItems::registerItems);
		CommonEvents.RECIPES_NAMESPACE_INIT.listen(Key.of(NPClaude.MOD_ID), () -> RecipeBuilder.initNameSpace(NPClaude.MOD_ID));
		CommonEvents.RECIPES_READY.listen(Key.of(NPClaude.MOD_ID), NpcItems::registerRecipes);
	}

	private static void registerItems() {
		guardianAmulet = new ItemBuilder(NPClaude.MOD_ID).setStackSize(1)
			.build(new Item("npclaude.guardian_amulet", NPClaude.MOD_ID + ":item/guardian_amulet", GUARDIAN_AMULET_ID));
		NPClaude.LOGGER.info("Registered the guardian amulet.");
	}

	/** A diamond set in gold, on a string. */
	private static void registerRecipes() {
		RecipeBuilder.Shaped(NPClaude.MOD_ID)
			.setShape(" S ", "GDG", " G ")
			.addInput('S', Items.STRING)
			.addInput('G', Items.INGOT_GOLD)
			.addInput('D', Items.DIAMOND)
			.create("guardian_amulet", new ItemStack(guardianAmulet));
	}

	public static boolean isGuardianAmulet(ItemStack stack) {
		return stack != null && guardianAmulet != null && stack.getItem() == guardianAmulet;
	}
}
