package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.util.helper.Direction;
import org.junit.jupiter.api.Test;

class FacingRuleTest {
	private static final Direction N = Direction.NORTH;
	private static final Direction E = Direction.EAST;
	private static final Direction S = Direction.SOUTH;
	private static final Direction W = Direction.WEST;

	/** The same kinds of rule BlockTurns uses, to check they all go full circle. */
	private static final List<FacingRule> RULES = List.of(
		FacingRule.table(3, 0, W, E, N, S),
		FacingRule.table(3, 0, S, W, N, E),
		FacingRule.table(7, 0, null, null, N, S, W, E),
		FacingRule.table(7, 0, null, W, E, N, S),
		FacingRule.directionId(7, 0),
		FacingRule.directionId(14, 1),
		FacingRule.AXIS,
		FacingRule.SIXTEENTHS,
		FacingRule.LEVER,
		FacingRule.RAIL,
		FacingRule.POWERED_RAIL);

	@Test
	void everyRuleGoesFullCircle() {
		for (FacingRule rule : RULES) {
			for (int meta = 0; meta < 256; meta++) {
				int once = rule.turn(meta, 1);
				assertEquals(meta, rule.turn(rule.turn(rule.turn(once, 1), 1), 1), "four quarter turns of " + meta);
				assertEquals(meta, rule.turn(once, 3), "a turn and three back of " + meta);
				assertEquals(rule.turn(once, 1), rule.turn(meta, 2), "two turns at once of " + meta);
			}
		}
	}

	@Test
	void stairsTurnAndKeepTheirOtherBits() {
		FacingRule stairs = FacingRule.table(3, 0, W, E, N, S);
		// Facing north (2), upside down (8): one turn clockwise faces east (1), still upside down.
		assertEquals(8 | 1, stairs.turn(8 | 2, 1));
		// A painted stair's color bits (above 4) stay.
		assertEquals(0x70 | 3, stairs.turn(0x70 | 1, 1));
	}

	@Test
	void flatOnesAreLeftAlone() {
		// A torch standing on the floor (5) has nowhere to turn.
		assertEquals(5, FacingRule.table(7, 0, null, W, E, N, S).turn(5, 1));
		// An upward-facing dispenser (1) stays up.
		assertEquals(1, FacingRule.directionId(7, 0).turn(1, 1));
		// A log standing up stays up; a lying one swaps axis.
		assertEquals(0, FacingRule.AXIS.turn(0, 1));
		assertEquals(2, FacingRule.AXIS.turn(1, 1));
	}

	@Test
	void railsAndLeversTurn() {
		// North-south track becomes east-west; a slope rising east (2) rises south (5); the east-south curve becomes south-west.
		assertEquals(1, FacingRule.RAIL.turn(0, 1));
		assertEquals(5, FacingRule.RAIL.turn(2, 1));
		assertEquals(7, FacingRule.RAIL.turn(6, 1));
		// A powered rail keeps its power bit: powered east-west (9) is not a curve.
		assertEquals(8 | 5, FacingRule.POWERED_RAIL.turn(8 | 2, 1));
		assertEquals(8, FacingRule.POWERED_RAIL.turn(8 | 1, 1));
		// A lever facing north (4) faces east (1); one lying north-south on a floor (5) lies east-west (6).
		assertEquals(1, FacingRule.LEVER.turn(4, 1));
		assertEquals(6, FacingRule.LEVER.turn(5, 1));
		assertEquals(16 | 8, FacingRule.LEVER.turn(16 | 7, 1));
	}

	@Test
	void signPostsTurnInSixteenths() {
		assertEquals(4, FacingRule.SIXTEENTHS.turn(0, 1));
		assertEquals(2, FacingRule.SIXTEENTHS.turn(14, 1));
		assertEquals(0x30 | 12, FacingRule.SIXTEENTHS.turn(0x30 | 8, 1));
	}
}
