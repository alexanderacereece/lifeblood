package lifeblood.nav;

/** One step of a path: the block space the feet should reach, at what height, and how. */
public record NavStep(int x, int y, int z, double feetY, NavMove move, boolean door) {
	public double centerX() {
		return this.x + 0.5;
	}

	public double centerZ() {
		return this.z + 0.5;
	}
}
