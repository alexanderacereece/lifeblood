package npclaude.personality;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemArmor;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemDiscMusic;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.item.tool.ItemTool;
import net.minecraft.core.item.tool.ItemToolSword;

/**
 * How much people like each item before personality comes into it: everyone loves a diamond and nobody wants a
 * handful of dirt. Each NPC then pulls these up or down by its traits and quirks, plus a little personal noise
 * (see {@link Tastes}).
 *
 * Items are sorted by name, so modded items land somewhere sensible too: "ingot_*" is a material, anything
 * "diamond" is valuable, unknown blocks are plain blocks.
 */
public final class ItemDefaults {
	/** A starting like (-1 to +1), the item's main category, and every category it belongs to. */
	public record Default(float base, TasteCategory main, Set<TasteCategory> categories, boolean giftable) {
		public boolean is(TasteCategory category) {
			return this.categories.contains(category);
		}
	}

	/** Exact names, checked first. */
	private static final Map<String, Rule> EXACT = Map.ofEntries(
		entry("dirt", -0.8F, TasteCategory.JUNK), entry("dirt_scorched", -0.8F, TasteCategory.JUNK),
		entry("dirt_scorched_rich", -0.7F, TasteCategory.JUNK), entry("path_dirt", -0.8F, TasteCategory.JUNK),
		entry("grass", -0.7F, TasteCategory.JUNK), entry("grass_retro", -0.7F, TasteCategory.JUNK),
		entry("grass_scorched", -0.7F, TasteCategory.JUNK), entry("mud", -0.7F, TasteCategory.JUNK, TasteCategory.GROSS),
		entry("gravel", -0.6F, TasteCategory.JUNK), entry("sand", -0.5F, TasteCategory.JUNK), entry("brimsand", -0.5F, TasteCategory.JUNK),
		entry("soulsand", -0.5F, TasteCategory.JUNK), entry("block_ash", -0.5F, TasteCategory.JUNK),
		entry("netherrack", -0.55F, TasteCategory.JUNK), entry("stone", -0.4F, TasteCategory.JUNK),
		entry("basalt", -0.4F, TasteCategory.JUNK), entry("limestone", -0.4F, TasteCategory.JUNK),
		entry("granite", -0.4F, TasteCategory.JUNK), entry("slate", -0.4F, TasteCategory.JUNK),
		entry("permafrost", -0.4F, TasteCategory.JUNK), entry("deadbush", -0.6F, TasteCategory.JUNK),
		entry("spinifex", -0.5F, TasteCategory.JUNK), entry("algae", -0.5F, TasteCategory.JUNK, TasteCategory.GROSS),
		entry("stick", -0.3F, TasteCategory.JUNK), entry("ammo_pebble", -0.4F, TasteCategory.JUNK),
		entry("cobweb", -0.6F, TasteCategory.JUNK, TasteCategory.GROSS), entry("boneshale", -0.5F, TasteCategory.JUNK),
		entry("ice", -0.3F, TasteCategory.BLOCK), entry("permaice", -0.3F, TasteCategory.BLOCK),
		entry("block_snow", -0.2F, TasteCategory.BLOCK), entry("cactus", -0.15F, TasteCategory.PLANT),

		entry("slimeball", -0.5F, TasteCategory.GROSS), entry("bone", -0.35F, TasteCategory.GROSS),
		entry("bone_pile", -0.5F, TasteCategory.GROSS), entry("sulfur", -0.3F, TasteCategory.GROSS),
		entry("food_porkchop_raw", -0.35F, TasteCategory.FOOD, TasteCategory.GROSS),
		entry("food_fish_raw", -0.35F, TasteCategory.FOOD, TasteCategory.GROSS),
		entry("food_venison_raw", -0.35F, TasteCategory.FOOD, TasteCategory.GROSS),
		entry("gunpowder", -0.1F, TasteCategory.DANGEROUS), entry("tnt", -0.2F, TasteCategory.DANGEROUS),
		entry("ammo_charge_explosive", -0.15F, TasteCategory.DANGEROUS),

		entry("diamond", 0.9F, TasteCategory.VALUABLE), entry("block_diamond", 0.95F, TasteCategory.VALUABLE),
		entry("block_gold", 0.85F, TasteCategory.VALUABLE), entry("ingot_gold", 0.6F, TasteCategory.VALUABLE),
		entry("ore_raw_gold", 0.35F, TasteCategory.VALUABLE), entry("quartz", 0.45F, TasteCategory.VALUABLE),
		entry("block_quartz", 0.6F, TasteCategory.VALUABLE), entry("olivine", 0.5F, TasteCategory.VALUABLE),
		entry("block_olivine", 0.65F, TasteCategory.VALUABLE), entry("rubyglass_crystal", 0.55F, TasteCategory.VALUABLE),
		entry("block_lapis", 0.4F, TasteCategory.VALUABLE), entry("dust_glowstone", 0.25F, TasteCategory.VALUABLE),
		entry("food_apple_gold", 0.75F, TasteCategory.FOOD, TasteCategory.VALUABLE),

		entry("ingot_iron", 0.3F, TasteCategory.MATERIAL), entry("ingot_steel", 0.4F, TasteCategory.MATERIAL),
		entry("ingot_steel_crude", 0.15F, TasteCategory.MATERIAL), entry("ore_raw_iron", 0.1F, TasteCategory.MATERIAL),
		entry("coal", 0.1F, TasteCategory.MATERIAL), entry("nethercoal", 0.15F, TasteCategory.MATERIAL),
		entry("block_coal", 0.15F, TasteCategory.MATERIAL), entry("block_charcoal", 0.1F, TasteCategory.MATERIAL),
		entry("block_iron", 0.5F, TasteCategory.MATERIAL), entry("block_steel", 0.6F, TasteCategory.MATERIAL),
		entry("leather", 0.15F, TasteCategory.MATERIAL), entry("string", 0.0F, TasteCategory.MATERIAL),
		entry("feather_chicken", 0.05F, TasteCategory.MATERIAL), entry("flint", 0.0F, TasteCategory.MATERIAL),
		entry("clay", 0.0F, TasteCategory.MATERIAL), entry("brick_clay", 0.05F, TasteCategory.MATERIAL),
		entry("dust_redstone", 0.15F, TasteCategory.MATERIAL), entry("chainlink", 0.1F, TasteCategory.MATERIAL),
		entry("cloth", 0.15F, TasteCategory.MATERIAL), entry("wool", 0.15F, TasteCategory.MATERIAL),
		entry("obsidian", 0.2F, TasteCategory.MATERIAL), entry("paper", 0.0F, TasteCategory.MATERIAL),
		entry("wheat", 0.0F, TasteCategory.MATERIAL), entry("sugarcane", 0.0F, TasteCategory.MATERIAL),
		entry("dust_sugar", 0.1F, TasteCategory.MATERIAL), entry("dough", 0.05F, TasteCategory.MATERIAL),
		entry("bowl", 0.0F, TasteCategory.MATERIAL), entry("rope", 0.1F, TasteCategory.MATERIAL),
		entry("dye", 0.1F, TasteCategory.MATERIAL), entry("egg_chicken", 0.0F, TasteCategory.MATERIAL),
		entry("glass", 0.05F, TasteCategory.MATERIAL),

		entry("tool_compass", 0.4F, TasteCategory.CURIO), entry("tool_clock", 0.4F, TasteCategory.CURIO),
		entry("tool_calendar", 0.35F, TasteCategory.CURIO), entry("map", 0.4F, TasteCategory.CURIO),
		entry("book", 0.3F, TasteCategory.CURIO), entry("bookshelf_planks_oak", 0.3F, TasteCategory.CURIO, TasteCategory.DECOR),
		entry("jar", 0.0F, TasteCategory.ITEM), entry("label", 0.0F, TasteCategory.ITEM), entry("basket", 0.1F, TasteCategory.ITEM),
		entry("timer", 0.1F, TasteCategory.CURIO),
		entry("tool_shears", 0.2F, TasteCategory.TOOL), entry("tool_shears_steel", 0.3F, TasteCategory.TOOL),
		entry("tool_fishingrod", 0.25F, TasteCategory.TOOL, TasteCategory.FUN),
		entry("tool_firestriker_iron", 0.15F, TasteCategory.TOOL), entry("tool_firestriker_steel", 0.2F, TasteCategory.TOOL),
		entry("paintbrush", 0.15F, TasteCategory.TOOL),

		entry("jukebox", 0.5F, TasteCategory.MUSIC), entry("noteblock", 0.25F, TasteCategory.MUSIC),
		entry("painting", 0.35F, TasteCategory.DECOR), entry("flag", 0.25F, TasteCategory.DECOR),
		entry("bed", 0.45F, TasteCategory.COMFORT), entry("seat", 0.25F, TasteCategory.COMFORT),
		entry("ammo_snowball", -0.1F, TasteCategory.FUN), entry("pumpkin_carved_idle", 0.35F, TasteCategory.FUN, TasteCategory.DECOR),
		entry("pumpkin_carved_active", 0.4F, TasteCategory.FUN, TasteCategory.DECOR),
		entry("armor_boots_iceskates", 0.35F, TasteCategory.FUN), entry("boat", 0.2F, TasteCategory.FUN),
		entry("saddle", 0.25F, TasteCategory.ITEM), entry("minecart", 0.15F, TasteCategory.ITEM),
		entry("pumpkin", 0.15F, TasteCategory.PLANT), entry("leaves_cherry_flowering", 0.3F, TasteCategory.FLOWER),
		entry("torch_coal", 0.1F, TasteCategory.ITEM),

		entry("food_cake", 0.55F, TasteCategory.FOOD), entry("cake", 0.55F, TasteCategory.FOOD),
		entry("food_cookie", 0.4F, TasteCategory.FOOD), entry("food_pumpkin_pie", 0.45F, TasteCategory.FOOD),
		entry("pumpkin_pie", 0.45F, TasteCategory.FOOD), entry("food_cherry", 0.3F, TasteCategory.FOOD),
		entry("food_apple", 0.2F, TasteCategory.FOOD), entry("food_bread", 0.2F, TasteCategory.FOOD),
		entry("food_stew_mushroom", 0.15F, TasteCategory.FOOD), entry("food_porkchop_cooked", 0.3F, TasteCategory.FOOD),
		entry("food_fish_cooked", 0.25F, TasteCategory.FOOD), entry("food_venison_cooked", 0.3F, TasteCategory.FOOD)
	);

	/** Name prefixes, checked in order when no exact name matched. */
	private static final List<Rule> PREFIXES = List.of(
		rule("cobble_", -0.55F, TasteCategory.JUNK), rule("tallgrass", -0.5F, TasteCategory.JUNK),
		rule("leaves_", -0.5F, TasteCategory.JUNK), rule("layer_", -0.5F, TasteCategory.JUNK),
		rule("moss_", -0.3F, TasteCategory.JUNK), rule("seeds_", -0.2F, TasteCategory.PLANT),
		rule("flower_", 0.4F, TasteCategory.FLOWER), rule("sapling_", 0.2F, TasteCategory.PLANT),
		rule("mushroom_", 0.0F, TasteCategory.PLANT), rule("record_", 0.5F, TasteCategory.MUSIC),
		rule("jar_butterfly", 0.45F, TasteCategory.CURIO), rule("lantern_firefly", 0.45F, TasteCategory.CURIO, TasteCategory.DECOR),
		rule("statue_", 0.35F, TasteCategory.DECOR), rule("ore_diamond", 0.75F, TasteCategory.VALUABLE),
		rule("ore_gold", 0.4F, TasteCategory.VALUABLE), rule("ore_", 0.05F, TasteCategory.MATERIAL),
		rule("log_", 0.05F, TasteCategory.MATERIAL), rule("planks_", 0.0F, TasteCategory.MATERIAL),
		rule("ammo_arrow", 0.1F, TasteCategory.WEAPON), rule("armor_quiver", 0.3F, TasteCategory.ARMOR),
		rule("handcannon", 0.35F, TasteCategory.WEAPON), rule("armor_wolf", -0.05F, TasteCategory.ITEM),
		rule("food_", 0.1F, TasteCategory.FOOD)
	);

	/** Never offered as anyone's favorite or least favorite: technical blocks and admin tools. */
	private static final List<String> TECHNICAL = List.of("wand_", "fluid_", "portal_", "fire", "air", "bedrock", "mobspawner",
		"piston_head", "piston_moving", "_upper", "_lower", "_top", "_bottom", "crops_", "_active", "glowing", "farmland", "overlay_",
		"handcannon_loaded", "sign_wall", "sign_post", "ammo_fireball", "repeater_", "motion_sensor", "lamp_inverted", "_idle",
		"wire_redstone", "rubyglass_node", "rubyglass_growth", "doors_", "matcher");

	private static final Map<Item, Default> CACHE = new IdentityHashMap<>();

	private ItemDefaults() {
	}

	public static Default of(Item item) {
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(item, ItemDefaults::resolve);
		}
	}

	private static Default resolve(Item item) {
		String name = nameOf(item);
		boolean giftable = isGiftable(name);
		Rule match = EXACT.get(name);
		if (match == null) {
			for (Rule rule : PREFIXES) {
				if (name.startsWith(rule.name())) {
					match = rule;
					break;
				}
			}
		}

		float base;
		EnumSet<TasteCategory> categories = EnumSet.noneOf(TasteCategory.class);
		TasteCategory main;
		if (match != null) {
			base = match.base();
			main = match.categories()[0];
			categories.addAll(List.of(match.categories()));
		} else if (item instanceof ItemToolSword || item instanceof ItemBow) {
			base = 0.05F + materialValue(name);
			main = TasteCategory.WEAPON;
		} else if (item instanceof ItemTool) {
			base = materialValue(name);
			main = TasteCategory.TOOL;
		} else if (item instanceof ItemArmor) {
			base = 0.05F + materialValue(name);
			main = TasteCategory.ARMOR;
		} else if (item instanceof ItemDiscMusic) {
			base = 0.5F;
			main = TasteCategory.MUSIC;
		} else if (Tastes.isFood(item)) {
			base = 0.1F;
			main = TasteCategory.FOOD;
		} else if (name.startsWith("slab_") || name.startsWith("stairs_") || name.startsWith("brick_") || name.startsWith("pressure_plate")
			|| name.startsWith("button_") || name.startsWith("trapdoor_") || name.startsWith("fence_")) {
			// Building parts, even fancy ones: a gold brick slab is a block first.
			base = name.contains("diamond") || name.contains("gold") ? 0.3F : 0.0F;
			main = TasteCategory.BLOCK;
		} else if (name.contains("diamond")) {
			base = 0.75F;
			main = TasteCategory.VALUABLE;
		} else if (name.contains("gold")) {
			base = 0.5F;
			main = TasteCategory.VALUABLE;
		} else if (item instanceof ItemBlock) {
			base = 0.0F;
			main = TasteCategory.BLOCK;
		} else {
			base = 0.05F;
			main = TasteCategory.ITEM;
		}

		categories.add(main);
		if (main != TasteCategory.VALUABLE && (name.contains("gold") || name.contains("diamond"))) {
			categories.add(TasteCategory.VALUABLE);
		}

		return new Default(base, main, categories, giftable);
	}

	/** Better materials make better presents. */
	private static float materialValue(String name) {
		if (name.contains("diamond")) {
			return 0.7F;
		} else if (name.contains("steel")) {
			return 0.5F;
		} else if (name.contains("gold")) {
			return 0.45F;
		} else if (name.contains("iron")) {
			return 0.35F;
		} else if (name.contains("chainmail")) {
			return 0.25F;
		} else if (name.contains("stone") || name.contains("leather")) {
			return 0.1F;
		}

		return 0.0F;
	}

	private static boolean isGiftable(String name) {
		for (String part : TECHNICAL) {
			if (name.contains(part)) {
				return false;
			}
		}

		return true;
	}

	/** The item's id without namespace or folder: "minecraft:item/diamond" is "diamond". */
	public static String nameOf(Item item) {
		if (item.namespaceID == null) {
			return Integer.toString(item.id);
		}

		String value = item.namespaceID.value();
		int slash = value.lastIndexOf('/');
		return slash >= 0 ? value.substring(slash + 1) : value;
	}

	/** A name (exact or prefix), its starting like, and its categories, the first being the main one. */
	private record Rule(String name, float base, TasteCategory[] categories) {
	}

	private static Map.Entry<String, Rule> entry(String name, float base, TasteCategory... categories) {
		return Map.entry(name, rule(name, base, categories));
	}

	private static Rule rule(String name, float base, TasteCategory... categories) {
		return new Rule(name, base, categories);
	}
}
