package lifeblood.travel.kinds;

import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;

/** Looking about a place once there: strolls to spots around it, pausing between, until the stay is up. */
public final class StrollAround implements NpcBehavior {
	public static final String ID = "stroll_around";
	private static final int MIN_PAUSE = 80;
	private static final int MAX_PAUSE = 300;

	private final int centerX;
	private final int centerZ;
	private final int radius;
	private final String what;
	private long nextMove;

	/** Strolls within a radius of a spot, described as what they're doing ("looking around", "visiting"). */
	public StrollAround(int centerX, int centerZ, int radius, String what) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = radius;
		this.what = what;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void tick(EntityNPC npc) {
		long now = npc.world.getWorldTime();
		if (npc.navigator.isMoving() || now < this.nextMove) {
			return;
		}

		this.nextMove = now + MIN_PAUSE + npc.getRandom().nextInt(MAX_PAUSE - MIN_PAUSE);
		for (int attempt = 0; attempt < 5; attempt++) {
			int x = this.centerX + npc.getRandom().nextInt(this.radius * 2 + 1) - this.radius;
			int z = this.centerZ + npc.getRandom().nextInt(this.radius * 2 + 1) - this.radius;
			if (npc.navigator.moveTo(x, npc.world.getHeightValue(x, z), z)) {
				return;
			}
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public String describe() {
		return this.what;
	}
}
