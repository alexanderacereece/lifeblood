package lifeblood.entity;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Where someone is in growing up, for how they talk: babbling babies (0–2), little kids (3–9), young teens (10–15),
 * older teens (16–17) and adults. Measured on the same 18-year scale as growing up, so it follows the
 * yearsToGrowUp setting.
 */
public enum AgeStage {
	BABY(3),
	CHILD(10),
	YOUNG_TEEN(16),
	TEEN(18),
	ADULT(Integer.MAX_VALUE);

	/** The age (out of 18 years of growing up) this stage lasts until. */
	private final int until;

	AgeStage(int until) {
		this.until = until;
	}

	/** The stage at this growth, 0 (newborn) to 1 (grown up). */
	public static AgeStage of(float growth) {
		if (growth >= 1.0F) {
			return ADULT;
		}

		float years = growth * 18.0F;
		for (AgeStage stage : values()) {
			if (years < stage.until) {
				return stage;
			}
		}

		return ADULT;
	}

	/** The name used in speech files: baby, child, young_teen, teen, adult. */
	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The stage with this {@link #id()}, or null for anything else (an empty or unknown saved value). */
	@Nullable
	public static AgeStage byId(String id) {
		for (AgeStage stage : values()) {
			if (stage.id().equals(id)) {
				return stage;
			}
		}

		return null;
	}
}
