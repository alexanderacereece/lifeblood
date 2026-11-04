package lifeblood.combat;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * How an NPC prefers to fight. AUTO picks from its equipment: a sword means melee, a bow (with arrows) means ranged,
 * both means whichever suits the moment.
 */
public enum CombatStyle {
	AUTO,
	MELEE,
	RANGED;

	public static CombatStyle byId(int id) {
		CombatStyle[] values = values();
		return id >= 0 && id < values.length ? values[id] : AUTO;
	}

	@Nullable
	public static CombatStyle byName(String name) {
		for (CombatStyle style : values()) {
			if (style.name().equalsIgnoreCase(name)) {
				return style;
			}
		}

		return null;
	}

	public CombatStyle next() {
		return values()[(this.ordinal() + 1) % values().length];
	}

	public String displayName() {
		return this.name().charAt(0) + this.name().substring(1).toLowerCase(Locale.ROOT);
	}
}
