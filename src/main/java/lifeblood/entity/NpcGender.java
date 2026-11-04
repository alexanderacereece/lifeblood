package lifeblood.entity;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** An NPC's gender. It picks the NPC's voice from BTA's player voice sets (the same ones players choose from). */
public enum NpcGender {
	MALE("mob.player.masculine.hurt", "mob.player.masculine.death"),
	FEMALE("mob.player.feminine.hurt", "mob.player.feminine.death");

	public final String hurtSound;
	public final String deathSound;

	NpcGender(String hurtSound, String deathSound) {
		this.hurtSound = hurtSound;
		this.deathSound = deathSound;
	}

	public static NpcGender byId(int id) {
		NpcGender[] values = values();
		return id >= 0 && id < values.length ? values[id] : MALE;
	}

	@Nullable
	public static NpcGender byName(String name) {
		for (NpcGender gender : values()) {
			if (gender.name().equalsIgnoreCase(name)) {
				return gender;
			}
		}

		return null;
	}

	public String displayName() {
		return this.name().charAt(0) + this.name().substring(1).toLowerCase(Locale.ROOT);
	}
}
