package lifeblood.building;

import java.util.ArrayList;
import java.util.List;

/**
 * The beds in a prefab, and which make a double bed: Minecraft has only single beds, so two side by side, facing the
 * same way, are a double bed for a couple. A single bed alone sleeps one. This is how a house says who it's for.
 */
public final class PrefabBeds {
	/** The short name beds are saved under (see PrefabCheck.shortName). */
	private static final String BED = "bed";
	/** Bed metadata: the facing in the low two bits (south, west, north, east, as the player's yaw), and the head bit. */
	private static final int DIRECTION_MASK = 3;
	private static final int HEAD_BIT = 8;
	/** Which way each bed facing points along x and z, foot to head: south, west, north, east. */
	private static final int[] STEP_X = {0, -1, 0, 1};
	private static final int[] STEP_Z = {1, 0, -1, 0};

	private PrefabBeds() {
	}

	/** One bed, at its foot's spot in the prefab, facing from foot to head (0 south, 1 west, 2 north, 3 east). */
	public record Bed(int x, int y, int z, int facing) {
		/** Lying side by side with another bed: the same facing, the same level, one step apart across the bed. */
		public boolean besides(Bed other) {
			if (other.facing != this.facing || other.y != this.y) {
				return false;
			}

			// Across the bed is along x for beds pointing north or south, along z for east or west.
			boolean alongZ = STEP_X[this.facing] == 0;
			return alongZ ? other.z == this.z && Math.abs(other.x - this.x) == 1 : other.x == this.x && Math.abs(other.z - this.z) == 1;
		}
	}

	/** Who the beds sleep: each double bed is two beds side by side, and the rest are singles. */
	public record Layout(List<Bed[]> doubles, List<Bed> singles) {
		/** How many people the house sleeps. */
		public int sleeps() {
			return this.doubles.size() * 2 + this.singles.size();
		}
	}

	/** Every bed in the prefab, found by its foot. */
	public static List<Bed> beds(Prefab prefab) {
		List<Bed> beds = new ArrayList<>();
		for (int y = 0; y < prefab.height; y++) {
			for (int z = 0; z < prefab.depth; z++) {
				for (int x = 0; x < prefab.width; x++) {
					int meta = prefab.metaAt(x, y, z);
					if ((meta & HEAD_BIT) == 0 && BED.equals(PrefabCheck.shortName(prefab.blockAt(x, y, z)))) {
						beds.add(new Bed(x, y, z, meta & DIRECTION_MASK));
					}
				}
			}
		}

		return beds;
	}

	/** Pairs beds lying side by side into doubles; a bed with neighbors on both sides pairs with the first found. */
	public static Layout layout(Prefab prefab) {
		List<Bed> beds = beds(prefab);
		List<Bed[]> doubles = new ArrayList<>();
		List<Bed> singles = new ArrayList<>();
		boolean[] paired = new boolean[beds.size()];
		for (int i = 0; i < beds.size(); i++) {
			if (paired[i]) {
				continue;
			}

			for (int j = i + 1; j < beds.size(); j++) {
				if (!paired[j] && beds.get(i).besides(beds.get(j))) {
					paired[i] = true;
					paired[j] = true;
					doubles.add(new Bed[] {beds.get(i), beds.get(j)});
					break;
				}
			}

			if (!paired[i]) {
				singles.add(beds.get(i));
			}
		}

		return new Layout(doubles, singles);
	}

	/** Where a bed's head is, one step from its foot the way it faces. */
	public static int[] head(Bed bed) {
		return new int[] {bed.x() + STEP_X[bed.facing()], bed.y(), bed.z() + STEP_Z[bed.facing()]};
	}
}
