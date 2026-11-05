package lifeblood.appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lifeblood.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/**
 * Which part (and which palette) an NPC wears on each layer. Saved and synced as a short string:
 * "base=human@tan;hair=bob@auburn;eyes=round@green;top=tunic@red", in drawing order. Empty means no parts.
 */
public final class Appearance {
	/** One layer's choice: a part, and a palette name ("" for none, or for parts that aren't recolorable). */
	public record Choice(String part, String palette) {
	}

	/** Chance of each optional layer, by gender: hair, facial hair, accessory. */
	private static final float MALE_HAIR = 0.92F;
	private static final float FEMALE_HAIR = 0.99F;
	private static final float MALE_FACIAL_HAIR = 0.35F;
	private static final float ACCESSORY = 0.15F;
	/** Chance of an outfit (top and bottom in one) instead of separate clothes, if there are outfits to wear. */
	private static final float OUTFIT = 0.35F;
	/** Chance an adult dyes their hair, any dye color, bright ones more often than muted. */
	private static final float DYED_HAIR = 0.04F;
	private static final float BRIGHT_HAIR_DYE = 0.7F;
	/** Chance of picking from clothes made for the wearer's climate, when there are any, over everyday wear. */
	private static final float CLIMATE_CLOTHES = 0.7F;

	private final Map<AppearanceLayer, Choice> choices;

	private Appearance(Map<AppearanceLayer, Choice> choices) {
		this.choices = choices;
	}

	public static final Appearance NONE = new Appearance(new EnumMap<>(AppearanceLayer.class));

	@Nullable
	public Choice get(AppearanceLayer layer) {
		return this.choices.get(layer);
	}

	public Map<AppearanceLayer, Choice> choices() {
		return Collections.unmodifiableMap(this.choices);
	}

	public boolean isEmpty() {
		return this.choices.isEmpty();
	}

	/** A copy with one layer changed; a null part removes the layer. */
	public Appearance with(AppearanceLayer layer, @Nullable String part, String palette) {
		Map<AppearanceLayer, Choice> copy = new EnumMap<>(AppearanceLayer.class);
		copy.putAll(this.choices);
		if (part == null) {
			copy.remove(layer);
		} else {
			copy.put(layer, new Choice(part, palette));
		}

		return new Appearance(copy);
	}

	public String encode() {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<AppearanceLayer, Choice> entry : this.choices.entrySet()) {
			if (!builder.isEmpty()) {
				builder.append(';');
			}

			builder.append(entry.getKey().id()).append('=').append(entry.getValue().part());
			if (!entry.getValue().palette().isEmpty()) {
				builder.append('@').append(entry.getValue().palette());
			}
		}

		return builder.toString();
	}

	public static Appearance decode(String text) {
		Map<AppearanceLayer, Choice> choices = new EnumMap<>(AppearanceLayer.class);
		for (String entry : text.split(";")) {
			int equals = entry.indexOf('=');
			if (equals <= 0) {
				continue;
			}

			AppearanceLayer layer = AppearanceLayer.byName(entry.substring(0, equals));
			if (layer == null) {
				continue;
			}

			String value = entry.substring(equals + 1);
			int at = value.indexOf('@');
			choices.put(layer, at < 0 ? new Choice(value, "") : new Choice(value.substring(0, at), value.substring(at + 1)));
		}

		return new Appearance(choices);
	}

	private static final String MUTED_PREFIX = "muted_";
	/** How often clothes get a muted dye rather than a bright one. */
	private static final float MUTED_CHANCE = 0.75F;
	/** How widely skin tones spread around the one most usual for a climate, in palettes. */
	private static final float SKIN_SPREAD = 1.3F;

	/**
	 * A random look from the parts that fit the wearer (their gender, job and climate; see Wearer), or NONE if there
	 * are no base parts yet. Hair and facial hair share a color, now and then dyed; eye and hair colors follow their
	 * palettes' weights, so brown eyes are common and violet ones rare. Clothes are a top and bottom with a dye each,
	 * or now and then an outfit that's both (used only if the part has recolorable pixels). Skin tone leans on how
	 * warm the place they're from is (0 cold to 1 hot, NaN for anywhere): paler in the cold, darker in the heat.
	 */
	public static Appearance random(PartCatalog catalog, Wearer wearer, Random random, float warmth) {
		Map<AppearanceLayer, Choice> choices = new EnumMap<>(AppearanceLayer.class);
		NpcGender gender = wearer.gender();
		if (catalog.parts(AppearanceLayer.BASE, wearer).isEmpty()) {
			return NONE;
		}

		HairColor hair = HairColor.natural(pickWeighted(catalog, "hair", random));
		if (random.nextFloat() < DYED_HAIR) {
			hair = hair.withDye(pickDye(catalog.paletteNames("dye"), random, BRIGHT_HAIR_DYE));
		}

		String hairColor = hair.encode();
		boolean outfit = !catalog.parts(AppearanceLayer.OUTFIT, wearer).isEmpty() && random.nextFloat() < OUTFIT;
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			float chance = switch (layer) {
				case HAIR -> gender == NpcGender.MALE ? MALE_HAIR : FEMALE_HAIR;
				case FACIAL_HAIR -> gender == NpcGender.MALE ? MALE_FACIAL_HAIR : 0.0F;
				case ACCESSORY -> ACCESSORY;
				case OUTFIT -> outfit ? 1.0F : 0.0F;
				case TOP, BOTTOM -> outfit ? 0.0F : 1.0F;
				default -> 1.0F;
			};
			List<PartCatalog.Part> parts = catalog.parts(layer, wearer);
			if (parts.isEmpty() || random.nextFloat() >= chance) {
				continue;
			}

			String palette = layer.paletteGroup == null ? "" : "hair".equals(layer.paletteGroup) ? hairColor
				: layer == AppearanceLayer.BASE ? pickSkin(catalog.paletteNames("skin"), random, warmth)
				: "dye".equals(layer.paletteGroup) ? pickDye(catalog.paletteNames("dye"), random, 1.0F - MUTED_CHANCE)
				: pickWeighted(catalog, layer.paletteGroup, random);
			choices.put(layer, new Choice(pickPart(parts, random), palette));
		}

		return new Appearance(choices);
	}

	/**
	 * A child's look: skin tone, hair color and eye color from one parent or the other, often a parent's hairstyle,
	 * eyes or face, and no facial hair (that can come with adulthood). Parents may be NONE (a player parent).
	 */
	public static Appearance inherit(PartCatalog catalog, NpcGender gender, Appearance first, Appearance second, Random random) {
		Wearer wearer = Wearer.of(gender);
		Appearance child = random(catalog, wearer, random, Float.NaN).with(AppearanceLayer.FACIAL_HAIR, null, "");
		List<Appearance> parents = new ArrayList<>();
		for (Appearance parent : new Appearance[] {first, second}) {
			if (!parent.isEmpty()) {
				parents.add(parent);
			}
		}

		if (parents.isEmpty() || child.isEmpty()) {
			return child;
		}

		String hairColor = null;
		for (AppearanceLayer layer : new AppearanceLayer[] {AppearanceLayer.BASE, AppearanceLayer.EYES, AppearanceLayer.MOUTH, AppearanceLayer.HAIR}) {
			Choice mine = child.get(layer);
			Choice theirs = parents.get(random.nextInt(parents.size())).get(layer);
			if (mine == null || theirs == null) {
				continue;
			}

			// The color always comes from a parent; the part itself sometimes, if it suits the child's gender.
			boolean suitsChild = catalog.parts(layer, wearer).stream().anyMatch(part -> part.name().equals(theirs.part()));
			String part = random.nextFloat() < 0.5F && suitsChild ? theirs.part() : mine.part();
			String palette = theirs.palette().isEmpty() ? mine.palette() : theirs.palette();
			if (layer == AppearanceLayer.HAIR) {
				// Born with the natural color: no parent's gray or dye.
				palette = HairColor.parse(palette).natural();
			}

			if (layer == AppearanceLayer.HAIR) {
				hairColor = palette;
			}

			child = child.with(layer, part, palette);
		}

		if (hairColor == null) {
			for (Appearance parent : parents) {
				Choice hair = parent.get(AppearanceLayer.HAIR);
				if (hair != null && !hair.palette().isEmpty()) {
					hairColor = HairColor.parse(hair.palette()).natural();
				}
			}
		}

		return hairColor != null ? child.withHairColor(hairColor) : child;
	}

	/** Its hair color (from the hair, or the beard if bald); null if it has neither. */
	@Nullable
	public HairColor hairColor() {
		Choice hair = this.get(AppearanceLayer.HAIR) != null ? this.get(AppearanceLayer.HAIR) : this.get(AppearanceLayer.FACIAL_HAIR);
		return hair != null && !hair.palette().isEmpty() ? HairColor.parse(hair.palette()) : null;
	}

	/** A copy with hair (and facial hair) recolored: a palette name, or an encoded HairColor. */
	public Appearance withHairColor(String palette) {
		Appearance result = this;
		for (AppearanceLayer layer : new AppearanceLayer[] {AppearanceLayer.HAIR, AppearanceLayer.FACIAL_HAIR}) {
			Choice choice = result.get(layer);
			if (choice != null) {
				result = result.with(layer, choice.part(), palette);
			}
		}

		return result;
	}

	/** Grown up: a man may grow facial hair, in his hair color. */
	public Appearance cameOfAge(PartCatalog catalog, NpcGender gender, Random random) {
		List<PartCatalog.Part> beards = catalog.parts(AppearanceLayer.FACIAL_HAIR, Wearer.of(gender));
		if (gender != NpcGender.MALE || beards.isEmpty() || this.get(AppearanceLayer.FACIAL_HAIR) != null || random.nextFloat() >= MALE_FACIAL_HAIR) {
			return this;
		}

		Choice hair = this.get(AppearanceLayer.HAIR);
		return this.with(AppearanceLayer.FACIAL_HAIR, beards.get(random.nextInt(beards.size())).name(), hair != null ? hair.palette() : "");
	}

	/** Skin palettes are listed palest first; the climate picks a spot along the list, and it's spread around that. */
	private static String pickSkin(List<String> skins, Random random, float warmth) {
		if (skins.isEmpty() || Float.isNaN(warmth)) {
			return pick(skins, random, "");
		}

		double spot = warmth * (skins.size() - 1) + random.nextGaussian() * SKIN_SPREAD;
		return skins.get((int) Math.max(0, Math.min(skins.size() - 1, Math.round(spot))));
	}

	/** A dye: a bright one with this chance, else a muted one (named "muted_..."). Clothes are usually muted. */
	private static String pickDye(List<String> dyes, Random random, float brightChance) {
		List<String> muted = dyes.stream().filter(name -> name.startsWith(MUTED_PREFIX)).toList();
		List<String> bright = dyes.stream().filter(name -> !name.startsWith(MUTED_PREFIX)).toList();
		boolean wantMuted = random.nextFloat() >= brightChance;
		List<String> from = wantMuted && !muted.isEmpty() || bright.isEmpty() ? muted : bright;
		return pick(from, random, "");
	}

	/** A palette from a group, by the weights in palettes.txt. */
	private static String pickWeighted(PartCatalog catalog, String group, Random random) {
		List<String> names = catalog.paletteNames(group);
		float total = 0.0F;
		for (String name : names) {
			total += catalog.weight(group, name);
		}

		float roll = random.nextFloat() * total;
		for (String name : names) {
			roll -= catalog.weight(group, name);
			if (roll < 0.0F) {
				return name;
			}
		}

		return pick(names, random, "");
	}

	/**
	 * A part for the wearer: their job's own, if it has any for this layer; otherwise clothes made for their
	 * climate most of the time, if there are any, and everyday wear the rest.
	 */
	static String pickPart(List<PartCatalog.Part> parts, Random random) {
		List<PartCatalog.Part> job = parts.stream().filter(part -> part.job() != null).toList();
		List<PartCatalog.Part> climate = parts.stream().filter(part -> part.job() == null && part.climate() != null).toList();
		List<PartCatalog.Part> everyday = parts.stream().filter(part -> part.job() == null && part.climate() == null).toList();
		List<PartCatalog.Part> from = !job.isEmpty() ? job
			: !climate.isEmpty() && (everyday.isEmpty() || random.nextFloat() < CLIMATE_CLOTHES) ? climate : everyday;
		return from.get(random.nextInt(from.size())).name();
	}

	private static String pick(List<String> options, Random random, String fallback) {
		return options.isEmpty() ? fallback : options.get(random.nextInt(options.size()));
	}
}
