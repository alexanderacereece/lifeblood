package npclaude.appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import npclaude.entity.NpcGender;
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
	 * A random look from the parts available, or NONE if there are no base parts yet. Hair and facial hair share a
	 * color; clothes are a top and bottom with a dye each, or now and then an outfit that's both (used only if the
	 * part has recolorable pixels). Skin tone leans
	 * on how warm the place they're from is (0 cold to 1 hot, NaN for anywhere): paler in the cold, darker in the heat.
	 */
	public static Appearance random(PartCatalog catalog, NpcGender gender, Random random, float warmth) {
		Map<AppearanceLayer, Choice> choices = new EnumMap<>(AppearanceLayer.class);
		if (catalog.parts(AppearanceLayer.BASE, gender).isEmpty()) {
			return NONE;
		}

		String hairColor = pick(catalog.paletteNames("hair"), random, "");
		boolean outfit = !catalog.parts(AppearanceLayer.OUTFIT, gender).isEmpty() && random.nextFloat() < OUTFIT;
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			float chance = switch (layer) {
				case HAIR -> gender == NpcGender.MALE ? MALE_HAIR : FEMALE_HAIR;
				case FACIAL_HAIR -> gender == NpcGender.MALE ? MALE_FACIAL_HAIR : 0.0F;
				case ACCESSORY -> ACCESSORY;
				case OUTFIT -> outfit ? 1.0F : 0.0F;
				case TOP, BOTTOM -> outfit ? 0.0F : 1.0F;
				default -> 1.0F;
			};
			List<String> parts = catalog.parts(layer, gender);
			if (parts.isEmpty() || random.nextFloat() >= chance) {
				continue;
			}

			String palette = layer.paletteGroup == null ? "" : "hair".equals(layer.paletteGroup) ? hairColor
				: layer == AppearanceLayer.BASE ? pickSkin(catalog.paletteNames("skin"), random, warmth)
				: "dye".equals(layer.paletteGroup) ? pickDye(catalog.paletteNames("dye"), random)
				: pick(catalog.paletteNames(layer.paletteGroup), random, "");
			choices.put(layer, new Choice(pick(parts, random, ""), palette));
		}

		return new Appearance(choices);
	}

	/**
	 * A child's look: skin tone, hair color and eye color from one parent or the other, often a parent's hairstyle,
	 * eyes or face, and no facial hair (that can come with adulthood). Parents may be NONE (a player parent).
	 */
	public static Appearance inherit(PartCatalog catalog, NpcGender gender, Appearance first, Appearance second, Random random) {
		Appearance child = random(catalog, gender, random, Float.NaN).with(AppearanceLayer.FACIAL_HAIR, null, "");
		List<Appearance> parents = new java.util.ArrayList<>();
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
			String part = random.nextFloat() < 0.5F && catalog.parts(layer, gender).contains(theirs.part()) ? theirs.part() : mine.part();
			String palette = theirs.palette().isEmpty() ? mine.palette() : theirs.palette();
			if (layer == AppearanceLayer.HAIR) {
				hairColor = palette;
			}

			child = child.with(layer, part, palette);
		}

		if (hairColor == null) {
			for (Appearance parent : parents) {
				Choice hair = parent.get(AppearanceLayer.HAIR);
				if (hair != null && !hair.palette().isEmpty()) {
					hairColor = hair.palette();
				}
			}
		}

		return hairColor != null ? child.withHairColor(hairColor) : child;
	}

	/** A copy with hair (and facial hair) recolored. */
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
		List<String> beards = catalog.parts(AppearanceLayer.FACIAL_HAIR, gender);
		if (gender != NpcGender.MALE || beards.isEmpty() || this.get(AppearanceLayer.FACIAL_HAIR) != null || random.nextFloat() >= MALE_FACIAL_HAIR) {
			return this;
		}

		Choice hair = this.get(AppearanceLayer.HAIR);
		return this.with(AppearanceLayer.FACIAL_HAIR, pick(beards, random, ""), hair != null ? hair.palette() : "");
	}

	/** Skin palettes are listed palest first; the climate picks a spot along the list, and it's spread around that. */
	private static String pickSkin(List<String> skins, Random random, float warmth) {
		if (skins.isEmpty() || Float.isNaN(warmth)) {
			return pick(skins, random, "");
		}

		double spot = warmth * (skins.size() - 1) + random.nextGaussian() * SKIN_SPREAD;
		return skins.get((int) Math.max(0, Math.min(skins.size() - 1, Math.round(spot))));
	}

	/** Clothes are usually muted: a muted dye (named "muted_...") most of the time, a bright one now and then. */
	private static String pickDye(List<String> dyes, Random random) {
		List<String> muted = dyes.stream().filter(name -> name.startsWith(MUTED_PREFIX)).toList();
		List<String> bright = dyes.stream().filter(name -> !name.startsWith(MUTED_PREFIX)).toList();
		boolean wantMuted = random.nextFloat() < MUTED_CHANCE;
		List<String> from = wantMuted && !muted.isEmpty() || bright.isEmpty() ? muted : bright;
		return pick(from, random, "");
	}

	private static String pick(List<String> options, Random random, String fallback) {
		return options.isEmpty() ? fallback : options.get(random.nextInt(options.size()));
	}
}
