package npclaude.personality;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemBucket;
import net.minecraft.core.item.ItemFood;
import org.jetbrains.annotations.Nullable;

/**
 * How much an NPC likes things, from -1 (hates it) to +1 (loves it): every item, plus kinds of weather, biomes
 * and seasons.
 *
 * Each like is a shared default (everyone loves diamonds, nobody wants dirt; see {@link ItemDefaults} and
 * {@link Surroundings}), pulled by the NPC's traits and quirks (greedy NPCs love valuables even more, timid ones
 * hate storms), plus personal noise derived from a per-NPC seed. Now and then the noise flips a default on its
 * head, so the odd NPC really doesn't mind dirt. Foods get much more noise than anything else, so favorite foods
 * vary a lot. Nothing is stored per item; only values set by command are saved.
 */
public class Tastes {
	private static final float ITEM_SPREAD = 0.35F;
	private static final float FOOD_SPREAD = 0.75F;
	private static final float ENVIRONMENT_SPREAD = 0.55F;
	/** Chance an item's default is turned upside down for this NPC. */
	private static final float ODDITY_CHANCE = 0.06F;
	/** Likes past this count as a real favorite or a real hate when picking one to talk about. */
	private static final float STRONG_FEELING = 0.35F;

	private final NpcPersonality owner;
	private long seed;
	/** Likes set explicitly; these win over the derived ones. Items by full id, the rest as "weather:storm" etc. */
	private final Map<String, Float> overrides = new HashMap<>();

	Tastes(NpcPersonality owner) {
		this.owner = owner;
	}

	public void reroll(Random random) {
		this.seed = random.nextLong();
		this.overrides.clear();
	}

	/**
	 * Whether this item is a food people have tastes about. Buckets count as food in BTA (for milk),
	 * but a bucket isn't a dish.
	 */
	/** How a liking sounds in speech files: love, like, meh, dislike or hate. */
	public static String describe(float like) {
		if (like >= 0.6F) {
			return "love";
		} else if (like >= 0.2F) {
			return "like";
		} else if (like > -0.2F) {
			return "meh";
		}

		return like > -0.6F ? "dislike" : "hate";
	}

	public static boolean isFood(Item item) {
		return item instanceof ItemFood && !(item instanceof ItemBucket);
	}

	// Items

	public float like(Item item) {
		String key = keyOf(item);
		Float override = this.overrides.get(key);
		if (override != null) {
			return override;
		}

		ItemDefaults.Default defaults = ItemDefaults.of(item);
		boolean food = defaults.is(TasteCategory.FOOD);
		float noise = this.noise(key, food ? FOOD_SPREAD * (this.owner.has(Quirk.GLUTTON) ? 1.2F : 1.0F) : ITEM_SPREAD);
		if (!food && this.uniform(key + "#odd") < ODDITY_CHANCE) {
			float direction = defaults.base() > 0.05F ? -1.0F : defaults.base() < -0.05F ? 1.0F : this.uniform(key + "#dir") < 0.5F ? -1.0F : 1.0F;
			noise += direction * (0.8F + 0.6F * this.uniform(key + "#size"));
		}

		return clamp(defaults.base() + this.pull(defaults) + noise);
	}

	/** How much this NPC's feeling about an item differs from most people's. */
	public float personal(Item item) {
		return this.like(item) - ItemDefaults.of(item).base();
	}

	public void set(Item item, float like) {
		this.overrides.put(keyOf(item), clamp(like));
	}

	/** How the NPC's traits and quirks pull its feelings about a kind of item. */
	private float pull(ItemDefaults.Default defaults) {
		NpcPersonality p = this.owner;
		float outlook = p.get(TraitAxis.OUTLOOK);
		float speech = p.get(TraitAxis.SPEECH);
		float courage = p.get(TraitAxis.COURAGE);
		float work = p.get(TraitAxis.WORK_ETHIC);
		float humor = p.get(TraitAxis.HUMOR);
		float pull = outlook * 0.05F + (p.has(Quirk.GENEROUS) ? 0.1F : 0.0F);
		if (defaults.is(TasteCategory.VALUABLE)) {
			pull += when(p, Quirk.GREEDY, 0.4F) + when(p, Quirk.PROUD, 0.15F) - when(p, Quirk.HUMBLE, 0.2F) - when(p, Quirk.GENEROUS, 0.15F);
		}

		if (defaults.is(TasteCategory.JUNK)) {
			pull += Math.max(0.0F, -speech) * 0.2F + work * 0.1F + when(p, Quirk.PIOUS, 0.15F) - when(p, Quirk.PROUD, 0.2F);
		}

		if (defaults.is(TasteCategory.GROSS)) {
			pull += Math.max(0.0F, -speech) * 0.35F - Math.max(0.0F, speech) * 0.2F;
		}

		if (defaults.is(TasteCategory.FOOD)) {
			pull += when(p, Quirk.GLUTTON, 0.3F);
		}

		if (p.has(Quirk.ANIMAL_LOVER) && (defaults.main() == TasteCategory.GROSS || isAnimalProduct(defaults))) {
			pull -= 0.25F;
		}

		if (defaults.is(TasteCategory.MATERIAL)) {
			pull += work * 0.15F;
		}

		if (defaults.is(TasteCategory.TOOL)) {
			pull += work * 0.3F;
		}

		if (defaults.is(TasteCategory.WEAPON)) {
			pull += courage * 0.3F - p.get(TraitAxis.MERCY) * 0.1F + when(p, Quirk.COMPETITIVE, 0.15F) + when(p, Quirk.HOTHEAD, 0.1F);
		}

		if (defaults.is(TasteCategory.ARMOR)) {
			pull += -courage * 0.15F + when(p, Quirk.PROUD, 0.1F) + when(p, Quirk.PARANOID, 0.15F);
		}

		if (defaults.is(TasteCategory.FLOWER)) {
			pull += when(p, Quirk.ROMANTIC, 0.35F) + when(p, Quirk.SENTIMENTAL, 0.15F) + humor * 0.1F - Math.max(0.0F, -speech) * 0.15F;
		}

		if (defaults.is(TasteCategory.PLANT)) {
			pull += work * 0.1F + when(p, Quirk.SENTIMENTAL, 0.1F);
		}

		if (defaults.is(TasteCategory.CURIO)) {
			pull += when(p, Quirk.CURIOUS, 0.35F) + when(p, Quirk.NOSY, 0.1F) + when(p, Quirk.SENTIMENTAL, 0.1F);
		}

		if (defaults.is(TasteCategory.MUSIC)) {
			pull += humor * 0.25F + p.get(TraitAxis.SOCIABILITY) * 0.15F + when(p, Quirk.SENTIMENTAL, 0.15F);
		}

		if (defaults.is(TasteCategory.DECOR)) {
			pull += speech * 0.15F + when(p, Quirk.PROUD, 0.1F) + when(p, Quirk.PIOUS, 0.1F);
		}

		if (defaults.is(TasteCategory.DANGEROUS)) {
			pull += courage * 0.3F + when(p, Quirk.HOTHEAD, 0.2F) - when(p, Quirk.ZEN, 0.1F) - when(p, Quirk.PARANOID, 0.2F);
		}

		if (defaults.is(TasteCategory.COMFORT)) {
			pull += -work * 0.35F;
		}

		if (defaults.is(TasteCategory.FUN)) {
			pull += humor * 0.3F + p.get(TraitAxis.SOCIABILITY) * 0.1F;
		}

		return pull;
	}

	// Weather, biomes and seasons

	public float like(Surroundings.Kind kind, String key) {
		String fullKey = kind.id() + ":" + key;
		Float override = this.overrides.get(fullKey);
		if (override != null) {
			return override;
		}

		return clamp(Surroundings.base(kind, key) + this.pull(kind, key) + this.noise(fullKey, ENVIRONMENT_SPREAD));
	}

	public void set(Surroundings.Kind kind, String key, float like) {
		this.overrides.put(kind.id() + ":" + key, clamp(like));
	}

	private float pull(Surroundings.Kind kind, String key) {
		NpcPersonality p = this.owner;
		float outlook = p.get(TraitAxis.OUTLOOK);
		float courage = p.get(TraitAxis.COURAGE);
		float humor = p.get(TraitAxis.HUMOR);
		float social = p.get(TraitAxis.SOCIABILITY);
		float work = p.get(TraitAxis.WORK_ETHIC);
		return switch (kind) {
			case WEATHER -> outlook * 0.1F + switch (key) {
				case "clear" -> social * 0.1F + work * 0.05F;
				case "rain" -> humor * 0.15F - social * 0.15F + when(p, Quirk.SENTIMENTAL, 0.1F);
				case "snow" -> humor * 0.2F + when(p, Quirk.SENTIMENTAL, 0.1F);
				case "storm" -> courage * 0.3F + when(p, Quirk.HOTHEAD, 0.15F) - when(p, Quirk.PARANOID, 0.15F);
				case "fog" -> -social * 0.15F - when(p, Quirk.PARANOID, 0.3F) + when(p, Quirk.CURIOUS, 0.1F);
				case "blood_rain" -> courage * 0.2F - when(p, Quirk.PIOUS, 0.2F) - when(p, Quirk.PARANOID, 0.15F);
				default -> 0.0F;
			};
			case BIOME -> outlook * 0.05F + switch (key) {
				// Creepy or cozy, depending on who you ask.
				case "cave" -> courage * 0.3F - social * 0.15F + when(p, Quirk.CURIOUS, 0.25F) - when(p, Quirk.PARANOID, 0.25F);
				case "ocean" -> when(p, Quirk.SENTIMENTAL, 0.15F) + when(p, Quirk.ROMANTIC, 0.1F) + humor * 0.1F + when(p, Quirk.ANIMAL_LOVER, 0.1F);
				case "forest", "birch_forest", "seasonal_forest", "meadow" -> when(p, Quirk.ANIMAL_LOVER, 0.2F) + when(p, Quirk.ZEN, 0.15F);
				default -> 0.0F;
			} + (Surroundings.base(kind, key) < 0.0F
				? when(p, Quirk.CURIOUS, 0.2F) - social * 0.1F - when(p, Quirk.GLUTTON, 0.1F)
				: social * 0.05F);
			case SEASON -> switch (key) {
				case "spring" -> when(p, Quirk.ROMANTIC, 0.2F) + outlook * 0.1F;
				case "summer" -> -work * 0.15F + when(p, Quirk.EARLY_BIRD, 0.15F) + social * 0.1F;
				case "fall" -> when(p, Quirk.SENTIMENTAL, 0.2F) + when(p, Quirk.GLUTTON, 0.15F) + work * 0.1F;
				case "winter" -> when(p, Quirk.NIGHT_OWL, 0.2F) + humor * 0.1F - social * 0.1F - outlook * 0.1F;
				default -> 0.0F;
			};
		};
	}

	/** The key this NPC likes most, or least, of one kind. */
	public String favorite(Surroundings.Kind kind, boolean most) {
		String best = null;
		float bestLike = 0.0F;
		for (String key : Surroundings.table(kind).keySet()) {
			float like = this.like(kind, key) * (most ? 1.0F : -1.0F);
			if (best == null || like > bestLike) {
				best = key;
				bestLike = like;
			}
		}

		return best;
	}

	// Rankings

	/** Every food in the game, most liked first. */
	public List<Item> rankedFoods() {
		return this.ranked(item -> ItemDefaults.of(item).is(TasteCategory.FOOD) && isFood(item));
	}

	/** Every giftable item that isn't food, most liked first. */
	public List<Item> rankedThings() {
		return this.ranked(item -> ItemDefaults.of(item).giftable() && !ItemDefaults.of(item).is(TasteCategory.FOOD));
	}

	private List<Item> ranked(Predicate<Item> filter) {
		List<Item> items = new ArrayList<>();
		for (Item item : Item.itemsList) {
			if (item != null && filter.test(item)) {
				items.add(item);
			}
		}

		Map<Item, Float> likes = new HashMap<>();
		for (Item item : items) {
			likes.put(item, this.like(item));
		}

		items.sort(Comparator.comparingDouble((Item item) -> likes.get(item)).reversed());
		return items;
	}

	@Nullable
	public Item favoriteFood() {
		List<Item> foods = this.rankedFoods();
		return foods.isEmpty() ? null : foods.get(0);
	}

	@Nullable
	public Item hatedFood() {
		List<Item> foods = this.rankedFoods();
		return foods.isEmpty() ? null : foods.get(foods.size() - 1);
	}

	/**
	 * The non-food thing that's most this NPC's own: something it loves, preferring what it loves more than most
	 * people do. So a curious NPC's favorite might be a compass rather than the diamond everyone likes.
	 */
	@Nullable
	public Item favoriteThing() {
		return this.mostPersonal(true);
	}

	/** The thing it hates, preferring what it hates more than most people do. */
	@Nullable
	public Item hatedThing() {
		return this.mostPersonal(false);
	}

	@Nullable
	private Item mostPersonal(boolean loved) {
		Item best = null;
		float bestScore = Float.NEGATIVE_INFINITY;
		float sign = loved ? 1.0F : -1.0F;
		for (Item item : Item.itemsList) {
			if (item == null || !isTalkingPoint(ItemDefaults.of(item))) {
				continue;
			}

			float like = this.like(item) * sign;
			if (like < STRONG_FEELING) {
				continue;
			}

			float score = like + this.personal(item) * sign;
			if (score > bestScore) {
				best = item;
				bestScore = score;
			}
		}

		return best;
	}

	/**
	 * Whether an item is the sort of thing someone would call a favorite: not food (that has its own favorite), and
	 * not one of the hundreds of plain building blocks and odds and ends, or everyone's favorite would be a granite
	 * brick slab.
	 */
	private static boolean isTalkingPoint(ItemDefaults.Default defaults) {
		return defaults.giftable() && !defaults.is(TasteCategory.FOOD) && defaults.main() != TasteCategory.BLOCK
			&& defaults.main() != TasteCategory.ITEM && defaults.main() != TasteCategory.MATERIAL;
	}

	// Noise

	/** Two hashed uniform numbers averaged: mostly near zero, rarely near the edges. */
	private float noise(String key, float spread) {
		return (this.uniform(key) + this.uniform(key + "#2") - 1.0F) * spread;
	}

	private float uniform(String key) {
		long hash = mix(this.seed ^ key.hashCode() * 0x9E3779B97F4A7C15L);
		return (float) ((hash >>> 11) * 0x1.0p-53);
	}

	/** SplitMix64's finalizer: scrambles every bit of the input into the output. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Leather, feathers, bones, raw meat: things that came off an animal. */
	private static boolean isAnimalProduct(ItemDefaults.Default defaults) {
		return defaults.is(TasteCategory.GROSS) || defaults.animal();
	}

	private static float when(NpcPersonality personality, Quirk quirk, float amount) {
		return personality.has(quirk) ? amount : 0.0F;
	}

	private static float clamp(float like) {
		return Math.max(-1.0F, Math.min(1.0F, like));
	}

	private static String keyOf(Item item) {
		return item.namespaceID != null ? item.namespaceID.toString() : Integer.toString(item.id);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("Seed", this.seed);
		CompoundTag overrideTag = new CompoundTag();
		for (Map.Entry<String, Float> entry : this.overrides.entrySet()) {
			overrideTag.putFloat(entry.getKey(), entry.getValue());
		}

		tag.putCompound("Set", overrideTag);
		return tag;
	}

	public void load(CompoundTag tag) {
		this.seed = tag.getLongOrDefault("Seed", this.seed);
		this.overrides.clear();
		if (tag.containsKey("Set")) {
			CompoundTag overrideTag = tag.getCompound("Set");
			for (String key : overrideTag.getValue().keySet()) {
				this.overrides.put(key, overrideTag.getFloat(key));
			}
		}
	}
}
