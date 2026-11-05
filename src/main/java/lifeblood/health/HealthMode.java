package lifeblood.health;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Whether an NPC can be hurt and killed. */
public enum HealthMode {
	/** Hurt and killed like anyone else. The default. */
	MORTAL,
	/** Hurt like anyone else, but a killing blow knocks them down instead (like Skyrim's essential characters). */
	INVINCIBLE,
	/** Takes no damage at all. For debugging. */
	INVULNERABLE;

	public static HealthMode byId(int id) {
		HealthMode[] values = values();
		return id >= 0 && id < values.length ? values[id] : MORTAL;
	}

	@Nullable
	public static HealthMode byName(String name) {
		for (HealthMode mode : values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}

		return null;
	}

	public String displayName() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
