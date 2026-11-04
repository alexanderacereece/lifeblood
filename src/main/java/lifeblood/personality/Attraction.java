package lifeblood.personality;

import java.util.Locale;
import java.util.Random;
import lifeblood.entity.NpcGender;
import org.jetbrains.annotations.Nullable;

/** Who an NPC can fall for, rolled once. Players don't set a gender, so anyone can fall for a player. */
public enum Attraction {
	OPPOSITE, SAME, BOTH;

	public static Attraction roll(Random random) {
		float roll = random.nextFloat();
		return roll < 0.8F ? OPPOSITE : roll < 0.9F ? SAME : BOTH;
	}

	public boolean allows(NpcGender self, @Nullable NpcGender other) {
		if (other == null) {
			return true;
		}

		return switch (this) {
			case OPPOSITE -> self != other;
			case SAME -> self == other;
			case BOTH -> true;
		};
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static Attraction byName(String name) {
		for (Attraction attraction : values()) {
			if (attraction.id().equalsIgnoreCase(name)) {
				return attraction;
			}
		}

		return null;
	}
}
