package npclaude.entity;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** An NPC's gender. Currently it changes the pitch of its voice (hurt and death sounds). */
public enum NpcGender {
	MALE(0.0F),
	FEMALE(0.45F);

	/** Added to the sound pitch, which is normally 1.0 with a little random variation. */
	public final float pitchOffset;

	NpcGender(float pitchOffset) {
		this.pitchOffset = pitchOffset;
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
