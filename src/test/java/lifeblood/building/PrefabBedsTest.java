package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.minecraft.core.util.helper.Direction;
import org.junit.jupiter.api.Test;

class PrefabBedsTest {
	private static final int HEAD = 8;
	private static final int SOUTH = 0;
	private static final int EAST = 3;

	/** A flat 4x4 prefab with beds: each {x, z, facing}, foot there and head one step the way it faces. */
	private static Prefab withBeds(int[]... beds) {
		short[] blocks = new short[16];
		short[] meta = new short[16];
		Prefab shape = new Prefab("shape", 4, 1, 4, Direction.SOUTH, List.of("minecraft:block/air", "minecraft:block/bed"), blocks, meta, Map.of());
		for (int[] bed : beds) {
			blocks[shape.index(bed[0], 0, bed[1])] = 1;
			meta[shape.index(bed[0], 0, bed[1])] = (short) bed[2];
			int[] head = PrefabBeds.head(new PrefabBeds.Bed(bed[0], 0, bed[1], bed[2]));
			blocks[shape.index(head[0], 0, head[2])] = 1;
			meta[shape.index(head[0], 0, head[2])] = (short) (bed[2] | HEAD);
		}

		return new Prefab("beds", 4, 1, 4, Direction.SOUTH, List.of("minecraft:block/air", "minecraft:block/bed"), blocks, meta, Map.of());
	}

	@Test
	void eachBedIsCountedOnceByItsFoot() {
		assertEquals(2, PrefabBeds.beds(withBeds(new int[] {0, 0, SOUTH}, new int[] {3, 0, SOUTH})).size());
	}

	@Test
	void twoBedsSideBySideFacingTheSameWayAreADouble() {
		PrefabBeds.Layout layout = PrefabBeds.layout(withBeds(new int[] {0, 0, SOUTH}, new int[] {1, 0, SOUTH}));
		assertEquals(1, layout.doubles().size());
		assertEquals(0, layout.singles().size());
		assertEquals(2, layout.sleeps());
	}

	@Test
	void bedsApartOrFacingDifferentWaysAreSingles() {
		assertEquals(2, PrefabBeds.layout(withBeds(new int[] {0, 0, SOUTH}, new int[] {2, 0, SOUTH})).singles().size());
		assertEquals(2, PrefabBeds.layout(withBeds(new int[] {0, 0, SOUTH}, new int[] {1, 0, EAST})).singles().size());
	}

	@Test
	void bedsEndToEndAreNotADouble() {
		// Pointing east, side by side means one step along z; one step along x is end to end.
		assertEquals(1, PrefabBeds.layout(withBeds(new int[] {0, 0, EAST}, new int[] {0, 1, EAST})).doubles().size());
		assertEquals(0, PrefabBeds.layout(withBeds(new int[] {0, 0, SOUTH}, new int[] {0, 2, SOUTH})).doubles().size());
	}

	@Test
	void threeInARowMakeADoubleAndASingle() {
		PrefabBeds.Layout layout = PrefabBeds.layout(withBeds(new int[] {0, 0, SOUTH}, new int[] {1, 0, SOUTH}, new int[] {2, 0, SOUTH}));
		assertEquals(1, layout.doubles().size());
		assertEquals(1, layout.singles().size());
	}
}
