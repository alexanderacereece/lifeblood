package lifeblood.travel.kinds;

import java.util.List;
import java.util.Random;
import net.minecraft.core.item.ItemStack;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.personality.TraitAxis;
import lifeblood.travel.Outing;
import lifeblood.travel.OutingKind;
import org.jetbrains.annotations.Nullable;

/**
 * A visit: the walk over to another homestead, a while spent around it (chatting with whoever lives there, as NPCs near
 * each other do), and the walk home. Extroverts visit most. Only homesteads within a few days' walk.
 */
public final class VisitKind implements OutingKind {
	public static final String ID = "visit";
	/** The farthest a homestead may be to be visited, in blocks. */
	private static final int MAX_DISTANCE = 500;
	/** How long they stay, in ticks: a few in-game hours. */
	private static final int MIN_STAY = 3000;
	private static final int MAX_STAY = 6000;
	private static final int AROUND_HOUSE = 6;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public float weight(EntityNPC npc) {
		return neighbor(npc) == null ? 0.0F : 1.0F + 0.6F * npc.personality.get(TraitAxis.SOCIABILITY);
	}

	@Override
	@Nullable
	public Outing plan(EntityNPC npc, Random random) {
		HomesteadRegister.Homestead neighbor = neighbor(npc);
		if (neighbor == null) {
			return null;
		}

		Outing outing = new Outing(ID);
		outing.destX = neighbor.x() + neighbor.width() / 2;
		outing.destY = neighbor.y() + 1;
		outing.destZ = neighbor.z() + neighbor.depth() / 2;
		outing.stay = MIN_STAY + random.nextInt(MAX_STAY - MIN_STAY);
		return outing;
	}

	/** The nearest other homestead within reach, or null. */
	@Nullable
	private static HomesteadRegister.Homestead neighbor(EntityNPC npc) {
		HomesteadRegister.Homestead best = null;
		double bestDistance = (double) MAX_DISTANCE * MAX_DISTANCE;
		for (HomesteadRegister.Homestead homestead : HomesteadRegister.get(npc.world).all()) {
			if (homestead.contains(npc.home.getX(), npc.home.getY(), npc.home.getZ())) {
				continue;
			}

			double dx = homestead.x() - npc.home.getX();
			double dz = homestead.z() - npc.home.getZ();
			if (dx * dx + dz * dz < bestDistance) {
				best = homestead;
				bestDistance = dx * dx + dz * dz;
			}
		}

		return best;
	}

	@Override
	public boolean packs(ItemStack stack) {
		return false;
	}

	@Override
	public NpcBehavior atDestination(Outing outing) {
		return new StrollAround(outing.destX, outing.destZ, AROUND_HOUSE, "visiting the neighbors");
	}

	@Override
	public Outcome simulate(Outing outing, EntityNPC npc, Random random) {
		return new Outcome(List.of(), 0.0F, false);
	}
}
