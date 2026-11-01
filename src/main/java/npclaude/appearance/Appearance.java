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

	/**
	 * A random look from the parts available, or NONE if there are no base parts yet. Hair and facial hair share a
	 * color; top and bottom each get their own dye (used only if the part has recolorable pixels).
	 */
	public static Appearance random(PartCatalog catalog, NpcGender gender, Random random) {
		Map<AppearanceLayer, Choice> choices = new EnumMap<>(AppearanceLayer.class);
		if (catalog.parts(AppearanceLayer.BASE, gender).isEmpty()) {
			return NONE;
		}

		String hairColor = pick(catalog.paletteNames("hair"), random, "");
		for (AppearanceLayer layer : AppearanceLayer.values()) {
			float chance = switch (layer) {
				case HAIR -> gender == NpcGender.MALE ? MALE_HAIR : FEMALE_HAIR;
				case FACIAL_HAIR -> gender == NpcGender.MALE ? MALE_FACIAL_HAIR : 0.0F;
				case ACCESSORY -> ACCESSORY;
				default -> 1.0F;
			};
			List<String> parts = catalog.parts(layer, gender);
			if (parts.isEmpty() || random.nextFloat() >= chance) {
				continue;
			}

			String palette = layer.paletteGroup == null ? "" : "hair".equals(layer.paletteGroup) ? hairColor
				: pick(catalog.paletteNames(layer.paletteGroup), random, "");
			choices.put(layer, new Choice(pick(parts, random, ""), palette));
		}

		return new Appearance(choices);
	}

	private static String pick(List<String> options, Random random, String fallback) {
		return options.isEmpty() ? fallback : options.get(random.nextInt(options.size()));
	}
}
