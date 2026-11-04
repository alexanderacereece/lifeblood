package lifeblood.personality;

/**
 * How far toward a pole a trait value sits. Lines are written against tiers, never exact values,
 * so a moderate NPC never pulls from both extremes.
 */
public enum TraitTier {
	/** Within 0.30 of the middle: only untagged lines. */
	NEUTRAL,
	/** 0.30 to 0.65 toward a pole. */
	LEAN,
	/** 0.65 or more toward a pole. */
	STRONG;

	public static final float LEAN_THRESHOLD = 0.30F;
	public static final float STRONG_THRESHOLD = 0.65F;

	public static TraitTier of(float value) {
		float distance = Math.abs(value);
		return distance >= STRONG_THRESHOLD ? STRONG : distance >= LEAN_THRESHOLD ? LEAN : NEUTRAL;
	}
}
