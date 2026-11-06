package lifeblood.personality;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lifeblood.entity.NpcPart;
import org.jetbrains.annotations.Nullable;

/**
 * Who an NPC is: eight trait axes, a few quirks, and tastes in food, things, weather and places. Fixed for life (mood, coming later,
 * shifts how the axes come across without changing them). See docs/personality.md.
 */
public class NpcPersonality implements NpcPart {
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
	/**
	 * Whether its orientation is decided yet. A child's isn't until it's old enough for a first crush (see
	 * NpcLife.ORIENTATION_AGE); until then it has no orientation quirk and falls for nobody.
	 */
	private boolean orientationDecided = true;
	public final Tastes tastes = new Tastes(this);

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
		if (this.quirks.contains(quirk) || !quirk.isOrientation() && this.countedQuirks() >= MAX_QUIRKS) {
			return false;
		}

		for (Quirk existing : this.quirks) {
			if (quirk.conflictsWith(existing)) {
				return false;
			}
		}

		return true;
	}

	/** Quirks that count toward the limit (orientation doesn't). */
	private int countedQuirks() {
		int count = 0;
		for (Quirk quirk : this.quirks) {
			if (!quirk.isOrientation()) {
				count++;
			}
		}

		return count;
	}

	/** Who it can fall for, from its orientation quirk (the opposite gender if it has none). */
	public Attraction attraction() {
		return this.has(Quirk.GAY) ? Attraction.SAME : this.has(Quirk.BISEXUAL) ? Attraction.BOTH : Attraction.OPPOSITE;
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
		List<Quirk> pool = quirkPool();
		Collections.shuffle(pool, random);
		for (Quirk quirk : pool) {
			if (this.quirks.size() >= count) {
				break;
			}

			this.addQuirk(quirk);
		}

		this.tastes.reroll(random);
		this.decideOrientation(random);
	}

	/** Every quirk that's rolled as a quirk (orientation is rolled separately). */
	private static List<Quirk> quirkPool() {
		List<Quirk> pool = new ArrayList<>(List.of(Quirk.values()));
		pool.removeIf(Quirk::isOrientation);
		return pool;
	}

	/** Whether its orientation is decided yet (a young child's isn't). */
	public boolean isOrientationDecided() {
		return this.orientationDecided;
	}

	/** Decides its orientation, once a child is old enough for a first crush: 10% gay, 10% bisexual. */
	public void decideOrientation(Random random) {
		this.rollOrientation(random);
		this.orientationDecided = true;
	}

	/** 10% gay, 10% bisexual. */
	private void rollOrientation(Random random) {
		this.quirks.remove(Quirk.GAY);
		this.quirks.remove(Quirk.BISEXUAL);
		Attraction attraction = Attraction.roll(random);
		if (attraction != Attraction.OPPOSITE) {
			this.quirks.add(attraction == Attraction.SAME ? Quirk.GAY : Quirk.BISEXUAL);
		}
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

	private void writeTo(CompoundTag tag) {
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
		tag.putBoolean("OrientationDecided", this.orientationDecided);
	}

	/**
	 * Loads a saved personality over the one it was just rolled with. Anything the save doesn't have (a trait axis
	 * added since, say) keeps its rolled value.
	 */
	private void fromTag(CompoundTag tag) {
		CompoundTag axisTag = tag.getCompound("Axes");
		for (TraitAxis axis : TraitAxis.values()) {
			if (axisTag.containsKey(axis.id())) {
				this.set(axis, axisTag.getFloat(axis.id()));
			}
		}

		if (tag.containsKey("Quirks")) {
			this.quirks.clear();
			for (String id : tag.getString("Quirks").split(",")) {
				Quirk quirk = Quirk.byName(id.trim());
				if (quirk != null) {
					this.addQuirk(quirk);
				}
			}
		}

		if (tag.containsKey("Tastes")) {
			this.tastes.load(tag.getCompound("Tastes"));
		}

		this.orientationDecided = !tag.containsKey("OrientationDecided") || tag.getBoolean("OrientationDecided");
	}

	/**
	 * A child's personality: each trait a blend of both parents plus its own shift (just one parent's, shifted
	 * further, if the other is a player), each parent quirk passed on 30% of the time, sometimes a quirk of its own,
	 * and new tastes.
	 */
	public void inherit(NpcPersonality first, @Nullable NpcPersonality second, Random random) {
		this.randomize(random);
		for (TraitAxis axis : TraitAxis.values()) {
			float parents = second != null ? (first.get(axis) + second.get(axis)) / 2.0F : first.get(axis);
			this.set(axis, parents + (float) random.nextGaussian() * (second != null ? 0.3F : 0.45F));
		}

		// Who a child will fall for isn't known until it's old enough for a first crush.
		this.quirks.removeIf(Quirk::isOrientation);
		this.orientationDecided = false;
		List<Quirk> inherited = new ArrayList<>(first.getQuirks());
		if (second != null) {
			inherited.addAll(second.getQuirks());
		}

		inherited.removeIf(Quirk::isOrientation);
		Collections.shuffle(inherited, random);
		for (Quirk quirk : inherited) {
			if (random.nextFloat() < 0.3F) {
				this.addQuirk(quirk);
			}
		}

		if (this.quirks.isEmpty() || random.nextFloat() < 0.25F) {
			List<Quirk> pool = quirkPool();
			Collections.shuffle(pool, random);
			for (Quirk quirk : pool) {
				if (this.addQuirk(quirk)) {
					break;
				}
			}
		}
	}

	@Override
	public String saveKey() {
		return "Personality";
	}

	@Override
	public void save(CompoundTag tag) {
		this.writeTo(tag);
	}

	@Override
	public void load(CompoundTag tag) {
		this.fromTag(tag);
	}
}
