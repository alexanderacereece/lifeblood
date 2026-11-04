package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.util.helper.Direction;
import org.junit.jupiter.api.Test;

class TurnsTest {
	@Test
	void directionsTurnClockwise() {
		assertEquals(Direction.EAST, Turns.turn(Direction.NORTH, 1));
		assertEquals(Direction.WEST, Turns.turn(Direction.SOUTH, 1));
		assertEquals(Direction.SOUTH, Turns.turn(Direction.NORTH, 2));
		assertEquals(Direction.NORTH, Turns.turn(Direction.EAST, 3));
		assertEquals(Direction.UP, Turns.turn(Direction.UP, 1));
		assertEquals(1, Turns.between(Direction.NORTH, Direction.EAST));
		assertEquals(3, Turns.between(Direction.NORTH, Direction.WEST));
		assertEquals(2, Turns.between(Direction.WEST, Direction.EAST));
	}

	@Test
	void spotsTurnWithTheBox() {
		// A 3 wide, 2 deep box. One turn clockwise: its north edge becomes the east edge of a 2 wide, 3 deep box.
		assertArrayEquals(new int[] {1, 0}, Turns.turnSpot(0, 0, 3, 2, 1));
		assertArrayEquals(new int[] {1, 2}, Turns.turnSpot(2, 0, 3, 2, 1));
		assertArrayEquals(new int[] {0, 0}, Turns.turnSpot(0, 1, 3, 2, 1));
		// Half a turn mirrors both ways.
		assertArrayEquals(new int[] {2, 1}, Turns.turnSpot(0, 0, 3, 2, 2));
		// Four turns go all the way around.
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 2; z++) {
				assertArrayEquals(new int[] {x, z}, Turns.turnSpot(x, z, 3, 2, 4));
			}
		}
	}

	@Test
	void yawGivesWhereThePlayerLooks() {
		assertEquals(Direction.SOUTH, Turns.facingOfYaw(0.0F));
		assertEquals(Direction.WEST, Turns.facingOfYaw(90.0F));
		assertEquals(Direction.NORTH, Turns.facingOfYaw(180.0F));
		assertEquals(Direction.EAST, Turns.facingOfYaw(-90.0F));
		assertEquals(Direction.SOUTH, Turns.facingOfYaw(370.0F));
	}
}
