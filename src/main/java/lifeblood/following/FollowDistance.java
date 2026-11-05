package lifeblood.following;

import java.util.Locale;

/** How closely an NPC follows: it starts walking once the player is further than {@code start}, and stops within {@code stop}. */
public enum FollowDistance {
	SHORT(2.5F, 4.0F),
	MEDIUM(4.0F, 7.0F),
	LONG(7.0F, 11.0F);

	public final float stop;
	public final float start;

	FollowDistance(float stop, float start) {
		this.stop = stop;
		this.start = start;
	}

	public static FollowDistance byId(int id) {
		FollowDistance[] values = values();
		return id >= 0 && id < values.length ? values[id] : SHORT;
	}

	public FollowDistance next() {
		return values()[(this.ordinal() + 1) % values().length];
	}

	public String displayName() {
		return this.name().charAt(0) + this.name().substring(1).toLowerCase(Locale.ROOT);
	}
}
