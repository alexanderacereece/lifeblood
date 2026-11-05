package lifeblood.util;

import org.joml.primitives.AABBdc;

/** Small geometry helpers shared by several systems: for now, whether a line passes through a box (line of fire). */
public final class Geometry {
	private Geometry() {
	}

	/** Slab test: does the segment from (x0,y0,z0) to (x1,y1,z1) pass through the box grown by margin? */
	public static boolean segmentHitsBox(double x0, double y0, double z0, double x1, double y1, double z1, AABBdc box, double margin) {
		double[] start = {x0, y0, z0};
		double[] delta = {x1 - x0, y1 - y0, z1 - z0};
		double[] min = {box.minX() - margin, box.minY() - margin, box.minZ() - margin};
		double[] max = {box.maxX() + margin, box.maxY() + margin, box.maxZ() + margin};
		double enter = 0.0;
		double exit = 1.0;
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(delta[axis]) < 1.0E-9) {
				if (start[axis] < min[axis] || start[axis] > max[axis]) {
					return false;
				}

				continue;
			}

			double t1 = (min[axis] - start[axis]) / delta[axis];
			double t2 = (max[axis] - start[axis]) / delta[axis];
			enter = Math.max(enter, Math.min(t1, t2));
			exit = Math.min(exit, Math.max(t1, t2));
			if (enter > exit) {
				return false;
			}
		}

		return true;
	}
}
