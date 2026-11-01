package npclaude.personality;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemBucket;
import net.minecraft.core.item.ItemFood;
import org.jetbrains.annotations.Nullable;

/**
 * How much an NPC likes each food, from -1 (hates it) to +1 (loves it).
 *
 * Tastes are derived from a per-NPC seed and the food's id rather than stored, so every food has a value,
 * including foods added later or by other mods. Only values changed afterwards (by command, for now) are saved.
 */
public class FoodPreferences {
	private long seed;
	/** Tastes set explicitly, by food id; these win over the derived ones. */
	private final Map<String, Float> overrides = new HashMap<>();

	public void reroll(Random random) {
		this.seed = random.nextLong();
		this.overrides.clear();
	}

	/**
	 * Whether this item is a food people have tastes about. Buckets count as food in BTA (for milk),
	 * but a bucket isn't a dish.
	 */
	public static boolean isFood(Item item) {
		return item instanceof ItemFood && !(item instanceof ItemBucket);
	}

	/** How much this NPC likes the food: -1 to +1. Non-food is 0. */
	public float like(Item item) {
		if (!isFood(item)) {
			return 0.0F;
		}

		String id = idOf(item);
		Float override = this.overrides.get(id);
		return override != null ? override : this.derive(id);
	}

	public void set(Item item, float like) {
		this.overrides.put(idOf(item), Math.max(-1.0F, Math.min(1.0F, like)));
	}

	/**
	 * Two hashed uniform numbers averaged: most foods are somewhere in the middle, real loves and hates are rarer.
	 */
	private float derive(String foodId) {
		long hash = mix(this.seed ^ foodId.hashCode() * 0x9E3779B97F4A7C15L);
		double first = (hash >>> 11) * 0x1.0p-53;
		double second = (mix(hash) >>> 11) * 0x1.0p-53;
		return (float) (first + second - 1.0);
	}

	/** SplitMix64's finalizer: scrambles every bit of the input into the output. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private static String idOf(Item item) {
		return item.namespaceID != null ? item.namespaceID.toString() : Integer.toString(item.id);
	}

	/** Every food in the game, most liked first. */
	public List<Item> ranked() {
		List<Item> foods = new ArrayList<>();
		for (Item item : Item.itemsList) {
			if (isFood(item)) {
				foods.add(item);
			}
		}

		foods.sort(Comparator.comparingDouble((Item item) -> this.like(item)).reversed());
		return foods;
	}

	@Nullable
	public Item favorite() {
		List<Item> foods = this.ranked();
		return foods.isEmpty() ? null : foods.get(0);
	}

	@Nullable
	public Item hated() {
		List<Item> foods = this.ranked();
		return foods.isEmpty() ? null : foods.get(foods.size() - 1);
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
