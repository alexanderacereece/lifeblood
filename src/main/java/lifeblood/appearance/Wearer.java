package lifeblood.appearance;

import lifeblood.entity.NpcGender;
import lifeblood.personality.Surroundings;
import org.jetbrains.annotations.Nullable;

/**
 * Who a look is rolled for, as far as clothes go: their gender, their job (null for none) and the climate they dress
 * for ("cold", "mild", "hot", or null if unknown). Parts tagged for a gender, job or climate (by the folders they're
 * in, see PartCatalog) are only rolled for a wearer who matches; untagged parts are everyday wear for anyone.
 */
public record Wearer(NpcGender gender, @Nullable String job, @Nullable String climate) {
	/** Someone with no job, dressing for a place this warm (0 cold to 1 hot, NaN if unknown). */
	public static Wearer of(NpcGender gender, float warmth) {
		return new Wearer(gender, null, Surroundings.climateOf(warmth));
	}

	/** Someone with no job, dressing for anywhere. */
	public static Wearer of(NpcGender gender) {
		return new Wearer(gender, null, null);
	}
}
