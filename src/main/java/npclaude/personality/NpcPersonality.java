package npclaude.personality;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Who an NPC is: eight trait axes, a few quirks, and tastes in food, things, weather and places. Fixed for life (mood, coming later,
 * shifts how the axes come across without changing them). See docs/personality.md.
 */
public class NpcPersonality {
	public static final int MAX_QUIRKS = 3;
	/**
	 * Spread of the bell curve trait values are rolled from: about 40% land neutral, a third lean, a quarter
	 * are strong, and a few hit the very end of the scale.
	 */
	private static final double AXIS_SPREAD = 0.55;
	/** Chance of 0, 1, 2 and 3 quirks. */
	private static final double[] QUIRK_COUNT_ODDS = {0.05, 0.55, 0.32, 0.08};

	private final Map<TraitAxis, Float> axes = new EnumMap<>(TraitAxis.class);
	private final Set<Quirk> quirks = EnumSet.noneOf(Quirk.class);
	public final Tastes tastes = new Tastes(this);
	/** Who it can fall for. */
	public Attraction attraction = Attraction.OPPOSITE;

	public NpcPersonality() {
		for (TraitAxis axis : TraitAxis.values()) {
			this.axes.put(axis, 0.0F);
		}
	}

	public float get(TraitAxis axis) {
		return this.axes.get(axis);
	}

	public void set(TraitAxis axis, float value) {
		this.axes.put(axis, Math.max(-1.0F, Math.min(1.0F, value)));
	}

	public TraitTier tier(TraitAxis axis) {
		return TraitTier.of(this.get(axis));
	}

	public boolean has(Quirk quirk) {
		return this.quirks.contains(quirk);
	}

	public Set<Quirk> getQuirks() {
		return Collections.unmodifiableSet(this.quirks);
	}

	/** Whether this quirk could be added: not already there, not clashing with one there, and room left. */
	public boolean canAdd(Quirk quirk) {
		if (this.quirks.contains(quirk) || this.quirks.size() >= MAX_QUIRKS) {
			return false;
		}

		for (Quirk existing : this.quirks) {
			if (quirk.conflictsWith(existing)) {
				return false;
			}
		}

		return true;
	}

	public boolean addQuirk(Quirk quirk) {
		return this.canAdd(quirk) && this.quirks.add(quirk);
	}

	public boolean removeQuirk(Quirk quirk) {
		return this.quirks.remove(quirk);
	}

	/** Rolls a whole new personality: axes from a bell curve, 0 to 3 quirks, and new tastes. */
	public void randomize(Random random) {
		for (TraitAxis axis : TraitAxis.values()) {
			this.set(axis, (float) (random.nextGaussian() * AXIS_SPREAD));
		}

		this.quirks.clear();
		int count = rollQuirkCount(random);
		List<Quirk> pool = new ArrayList<>(List.of(Quirk.values()));
		Collections.shuffle(pool, random);
		for (Quirk quirk : pool) {
			if (this.quirks.size() >= count) {
				break;
			}

			this.addQuirk(quirk);
		}

		this.tastes.reroll(random);
		this.attraction = Attraction.roll(random);
	}

	private static int rollQuirkCount(Random random) {
		double roll = random.nextDouble();
		for (int count = 0; count < QUIRK_COUNT_ODDS.length; count++) {
			roll -= QUIRK_COUNT_ODDS[count];
			if (roll < 0.0) {
				return count;
			}
		}

		return 1;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag axisTag = new CompoundTag();
		for (TraitAxis axis : TraitAxis.values()) {
			axisTag.putFloat(axis.id(), this.get(axis));
		}

		List<String> quirkIds = new ArrayList<>();
		for (Quirk quirk : this.quirks) {
			quirkIds.add(quirk.id());
		}

		tag.putCompound("Axes", axisTag);
		tag.putString("Quirks", String.join(",", quirkIds));
		tag.putCompound("Tastes", this.tastes.save());
		tag.putString("Attraction", this.attraction.id());
		return tag;
	}

	/**
	 * Loads a saved personality. Saves from before trait axes only had bravery (0 to 1): those NPCs keep the
	 * personality they were just rolled, with their bravery carried over as courage.
	 */
	public void load(CompoundTag tag) {
		if (!tag.containsKey("Axes")) {
			if (tag.containsKey("Bravery")) {
				this.setFromBravery(tag.getFloat("Bravery"));
			}

			return;
		}

		CompoundTag axisTag = tag.getCompound("Axes");
		for (TraitAxis axis : TraitAxis.values()) {
			this.set(axis, axisTag.getFloatOrDefault(axis.id(), 0.0F));
		}

		this.quirks.clear();
		for (String id : tag.getStringOrDefault("Quirks", "").split(",")) {
			Quirk quirk = Quirk.byName(id.trim());
			if (quirk != null) {
				this.addQuirk(quirk);
			}
		}

		Attraction attraction = Attraction.byName(tag.getStringOrDefault("Attraction", ""));
		if (attraction != null) {
			this.attraction = attraction;
		}

		if (tag.containsKey("Tastes")) {
			this.tastes.load(tag.getCompound("Tastes"));
		} else if (tag.containsKey("Food")) {
			this.tastes.load(tag.getCompound("Food"));
		}
	}

	/** Converts the old 0-to-1 bravery into the courage axis. */
	public void setFromBravery(float bravery) {
		this.set(TraitAxis.COURAGE, bravery * 2.0F - 1.0F);
	}
}
