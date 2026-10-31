package npclaude.nav;

/** How a path step is reached from the one before it. */
public enum NavMove {
	/** Walking on level ground, or stepping up a slab-height ledge. */
	WALK,
	/** Jumping up onto a block up to about one block higher. */
	JUMP_UP,
	/** Walking off a ledge and falling up to 3 blocks (no fall damage). */
	DROP,
	/** Jumping across a one-block gap. */
	GAP_JUMP,
	/** Moving through deep water. */
	SWIM,
	/** Going up a ladder (or swimming straight up). */
	CLIMB,
	/** Going down a ladder (or sinking). */
	DESCEND
}
