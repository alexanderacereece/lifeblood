package npclaude.appearance;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The layers an NPC's look is built from, in drawing order (later layers draw over earlier ones). Each recolorable
 * layer names the palette group its colors come from. The mouth always takes the base's skin color and facial hair
 * the hair's color, so they match. An outfit is a top and bottom in one (a uniform, a dress, overalls): wearing one
 * hides the separate top and bottom.
 */
public enum AppearanceLayer {
	BASE("skin"),
	EYES("eyes"),
	MOUTH("skin"),
	FACIAL_HAIR("hair"),
	BOTTOM("dye"),
	TOP("dye"),
	OUTFIT("dye"),
	HAIR("hair"),
	ACCESSORY(null);

	/** The palette group that recolors this layer, or null if it's always drawn as is. */
	@Nullable
	public final String paletteGroup;

	AppearanceLayer(@Nullable String paletteGroup) {
		this.paletteGroup = paletteGroup;
	}

	/** The layer whose palette this one uses instead of its own, if any: the mouth uses the skin's, a beard the hair's. */
	@Nullable
	public AppearanceLayer paletteFrom() {
		return switch (this) {
			case MOUTH -> BASE;
			case FACIAL_HAIR -> HAIR;
			default -> null;
		};
	}

	/** Whether an outfit covers this layer, so it isn't drawn while one is worn. */
	public boolean coveredByOutfit() {
		return this == TOP || this == BOTTOM;
	}

	/** Its folder name: "facial_hair". */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static AppearanceLayer byName(String name) {
		for (AppearanceLayer layer : values()) {
			if (layer.id().equalsIgnoreCase(name)) {
				return layer;
			}
		}

		return null;
	}
}
