package lifeblood.building;

import net.minecraft.core.util.helper.Direction;

/**
 * How one kind of block stores its facing in metadata, and so how to turn it. Only the facing bits change; everything
 * else in the metadata (color, open, powered, upper half) is kept. See {@link BlockTurns} for which block uses which.
 */
@FunctionalInterface
public interface FacingRule {
	/** The metadata after this many clockwise quarter turns (1 to 3). */
	int turn(int meta, int turns);

	/**
	 * Facing as a small number in the masked bits, where each value stands for a direction (null for
	 * values with no horizontal facing, like a torch standing on the floor, which are left alone).
	 */
	static FacingRule table(int mask, int shift, Direction... byValue) {
		return (meta, turns) -> {
			int value = (meta & mask) >> shift;
			if (value >= byValue.length || Turns.indexOf(byValue[value]) < 0) {
				return meta;
			}

			Direction turned = Turns.turn(byValue[value], turns);
			for (int i = 0; i < byValue.length; i++) {
				if (byValue[i] == turned) {
					return meta & ~mask | (i << shift) & mask;
				}
			}

			return meta;
		};
	}

	/** Facing as a full direction id (0 down, 1 up, 2 north, 3 south, 4 west, 5 east) in the masked bits. */
	static FacingRule directionId(int mask, int shift) {
		return (meta, turns) -> {
			Direction direction = Direction.fromId((meta & mask) >> shift);
			if (Turns.indexOf(direction) < 0) {
				return meta;
			}

			return meta & ~mask | (Turns.turn(direction, turns).id << shift) & mask;
		};
	}

	/** An axis (0 up, 1 north-south, 2 east-west): an odd number of turns swaps the two flat ones. */
	FacingRule AXIS = (meta, turns) -> {
		if ((turns & 1) == 0 || meta != 1 && meta != 2) {
			return meta;
		}

		return meta == 1 ? 2 : 1;
	};

	/** Sixteenths of a full turn, like a sign on a post: four per quarter turn. */
	FacingRule SIXTEENTHS = (meta, turns) -> meta & ~15 | (meta + 4 * turns) & 15;

	/**
	 * Levers: 1 to 4 face east, west, south, north; 5 and 6 lie on top of a block along north-south or east-west, and 7
	 * and 8 hang under one the same way. Odd turns swap the two lying ones.
	 */
	FacingRule LEVER = (meta, turns) -> {
		int rotation = meta & 15;
		int turned = switch (rotation) {
			case 1, 2, 3, 4 -> {
				Direction[] byValue = {null, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
				Direction direction = Turns.turn(byValue[rotation], turns);
				int value = rotation;
				for (int i = 1; i < byValue.length; i++) {
					if (byValue[i] == direction) {
						value = i;
					}
				}

				yield value;
			}
			case 5, 6 -> (turns & 1) == 1 ? 11 - rotation : rotation;
			case 7, 8 -> (turns & 1) == 1 ? 15 - rotation : rotation;
			default -> rotation;
		};
		return meta & ~15 | turned;
	};

	/**
	 * Plain rails, in the low four bits: 0 north-south, 1 east-west, 2 to 5 sloping up to the east, west, north and
	 * south, and 6 to 9 curves joining east-south, west-south, west-north and east-north.
	 */
	FacingRule RAIL = (meta, turns) -> {
		int shape = meta & 15;
		if (shape >= 6 && shape <= 9) {
			// Each clockwise turn moves a curve one step along: east-south, south-west, west-north, north-east.
			return meta & ~15 | 6 + (shape - 6 + turns & 3);
		}

		return shape <= 5 ? meta & ~15 | straightRail(shape, turns) : meta;
	};

	/** Powered and detector rails: straight or sloped only (0 to 5, as for plain rails) in the low three bits, bit 8 for power. */
	FacingRule POWERED_RAIL = (meta, turns) -> meta & ~7 | straightRail(meta & 7, turns);

	/** A straight or sloped rail shape (0 to 5) turned; anything else is returned as it is. */
	private static int straightRail(int shape, int turns) {
		return switch (shape) {
			case 0, 1 -> (turns & 1) == 1 ? 1 - shape : shape;
			case 2, 3, 4, 5 -> {
				// Slopes in clockwise order of the side they rise toward: north (4), east (2), south (5), west (3).
				int[] clockwise = {4, 2, 5, 3};
				int index = 0;
				for (int i = 0; i < 4; i++) {
					if (clockwise[i] == shape) {
						index = i;
					}
				}

				yield clockwise[(index + turns) & 3];
			}
			default -> shape;
		};
	}
}
