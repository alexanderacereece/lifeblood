package npclaude.combat;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** How two factions regard each other. Members of the same faction are always allies. */
public enum FactionRelation {
	/** Fight side by side and count each other's strength. */
	ALLY,
	/** Ignore each other unless one attacks the other. */
	NEUTRAL,
	/** Attack on sight. */
	HOSTILE;

	@Nullable
	public static FactionRelation byName(String name) {
		for (FactionRelation relation : values()) {
			if (relation.name().equalsIgnoreCase(name)) {
				return relation;
			}
		}

		return null;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}
}
