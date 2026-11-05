package lifeblood.faction;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.util.helper.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * A faction's two colors: the primary one and a secondary one. Buildings that belong to a faction are painted in them
 * (see building/Swaps: gray becomes the primary color, light gray the secondary), and later banners and outfits can be.
 */
public record FactionColors(DyeColor primary, DyeColor secondary) {
	public static final FactionColors SETTLERS = new FactionColors(DyeColor.BLUE, DyeColor.WHITE);
	public static final FactionColors RAIDERS = new FactionColors(DyeColor.RED, DyeColor.BLACK);

	/** Colors a new faction can be given: every dye except the two grays, which prefabs use as placeholders. */
	private static final List<DyeColor> CHOICES = DyeColor.blockOrderedColors().stream()
		.filter(color -> color != DyeColor.GRAY && color != DyeColor.SILVER)
		.toList();

	/** Colors for a new faction, picked from its name so the same name always gets the same pair. */
	public static FactionColors forNewFaction(String faction) {
		int hash = faction.hashCode() & Integer.MAX_VALUE;
		DyeColor primary = CHOICES.get(hash % CHOICES.size());
		DyeColor secondary = CHOICES.get((hash / CHOICES.size() % (CHOICES.size() - 1) + 1 + CHOICES.indexOf(primary)) % CHOICES.size());
		return new FactionColors(primary, secondary);
	}

	/** The name a color is written as in commands and saves ("lightblue", "silver"...). */
	public static String nameOf(DyeColor color) {
		return color.colorID;
	}

	/** The dye with this name, or null; "light_gray" and "lightgray" are accepted for BTA's "silver". */
	@Nullable
	public static DyeColor byName(String name) {
		String key = name.toLowerCase(Locale.ROOT).replace("_", "");
		if (key.equals("lightgray") || key.equals("lightgrey")) {
			return DyeColor.SILVER;
		}

		for (DyeColor color : DyeColor.values()) {
			if (color.colorID.equals(key) || color.name().toLowerCase(Locale.ROOT).replace("_", "").equals(key)) {
				return color;
			}
		}

		return null;
	}
}
