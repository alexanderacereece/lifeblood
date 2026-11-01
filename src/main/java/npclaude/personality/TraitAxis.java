package npclaude.personality;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * The core of an NPC's character: eight scales from -1 to +1. By convention the + pole is the conventionally
 * "positive" one, but neither side is better. See docs/personality.md for what each one does.
 */
public enum TraitAxis {
	OUTLOOK("pessimist", "optimist"),
	MANNERS("rude", "polite"),
	SPEECH("crude", "proper"),
	COURAGE("timid", "brave"),
	SOCIABILITY("introvert", "extrovert"),
	HUMOR("serious", "lighthearted"),
	MERCY("vengeful", "forgiving"),
	WORK_ETHIC("lazy", "hardworking");

	/** The - pole. */
	public final String negative;
	/** The + pole. */
	public final String positive;

	TraitAxis(String negative, String positive) {
		this.negative = negative;
		this.positive = positive;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return this.id().replace('_', ' ');
	}

	/** The pole this value leans toward, or null in the neutral band. */
	@Nullable
	public String poleOf(float value) {
		if (TraitTier.of(value) == TraitTier.NEUTRAL) {
			return null;
		}

		return value < 0.0F ? this.negative : this.positive;
	}

	@Nullable
	public static TraitAxis byName(String name) {
		for (TraitAxis axis : values()) {
			if (axis.id().equalsIgnoreCase(name)) {
				return axis;
			}
		}

		return null;
	}
}
