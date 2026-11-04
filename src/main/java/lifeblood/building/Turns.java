package lifeblood.building;

import net.minecraft.core.util.helper.Direction;

/**
 * Quarter turns clockwise, seen from above (north, east, south, west), and what they do to positions and directions.
 * Everything that rotates a prefab goes through here so one convention holds everywhere: north is -z, east is +x.
 */
public final class Turns {
	/** The horizontal directions in clockwise order. */
	private static final Direction[] CLOCKWISE = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private Turns() {
	}

	/** Brings any number of turns into 0 to 3. */
	public static int normalize(int turns) {
		return turns & 3;
	}

	/** A horizontal direction turned clockwise; up, down and none stay as they are. */
	public static Direction turn(Direction direction, int turns) {
		int index = indexOf(direction);
		return index < 0 ? direction : CLOCKWISE[(index + turns) & 3];
	}

	/** How many clockwise turns take one horizontal direction to another (0 if either isn't horizontal). */
	public static int between(Direction from, Direction to) {
		int a = indexOf(from);
		int b = indexOf(to);
		return a < 0 || b < 0 ? 0 : (b - a) & 3;
	}

	/** Position 0 north, 1 east, 2 south, 3 west; -1 for anything else. */
	public static int indexOf(Direction direction) {
		for (int i = 0; i < CLOCKWISE.length; i++) {
			if (CLOCKWISE[i] == direction) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Where a spot in a box of the given width (x) and depth (z) ends up after turning the box, measured from the
	 * turned box's own corner. Returns {x, z}.
	 */
	public static int[] turnSpot(int x, int z, int width, int depth, int turns) {
		int tx = x;
		int tz = z;
		int w = width;
		int d = depth;
		for (int i = 0; i < normalize(turns); i++) {
			// One turn clockwise: the north edge (z = 0) becomes the east edge.
			int nx = d - 1 - tz;
			tz = tx;
			tx = nx;
			int swap = w;
			w = d;
			d = swap;
		}

		return new int[] {tx, tz};
	}

	/** One step this way along x: east is +1, west -1. */
	public static int stepX(Direction direction) {
		return direction == Direction.EAST ? 1 : direction == Direction.WEST ? -1 : 0;
	}

	/** One step this way along z: south is +1, north -1. */
	public static int stepZ(Direction direction) {
		return direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? -1 : 0;
	}

	/** The facing a player looking this way (yaw, in degrees) is looking. */
	public static Direction facingOfYaw(float yaw) {
		// Yaw 0 looks south and goes clockwise: south, west, north, east.
		int quarter = (int) Math.floor(yaw * 4.0F / 360.0F + 0.5) & 3;
		return switch (quarter) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			default -> Direction.EAST;
		};
	}
}
