package npclaude.appearance;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The layers an NPC's look is built from, in drawing order (later layers draw over earlier ones). Each recolorable
 * layer names the palette group its colors come from; hair and facial hair share one hair color.
 */
public enum AppearanceLayer {
	BASE("skin"),
	EYES("eyes"),
	MOUTH(null),
	FACIAL_HAIR("hair"),
	BOTTOM("dye"),
	TOP("dye"),
	HAIR("hair"),
	ACCESSORY(null);

	/** The palette group that recolors this layer, or null if it's always drawn as is. */
	@Nullable
	public final String paletteGroup;

	AppearanceLayer(@Nullable String paletteGroup) {
		this.paletteGroup = paletteGroup;
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
