package lifeblood.homestead.life;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;
import lifeblood.work.Approach;
import lifeblood.work.BlockUse;
import lifeblood.work.Containers;

/**
 * The trips to the house that chores begin and end with: fetching a tool from the household's chests, and putting
 * away what was gathered. Each call is one tick; the Approach walks them there.
 */
final class Errands {
	private Errands() {
	}

	/** How a trip is going. */
	enum Trip {
		DONE, ON_THE_WAY, FAILED
	}

	/**
	 * Gets a tool matching into the hand: from the pack if they carry one, else from a household chest. FAILED if there
	 * isn't one to be had (many chores go ahead bare-handed then, as a player can).
	 */
	static Trip fetchTool(EntityNPC npc, Approach approach, Predicate<ItemStack> tool) {
		if (BlockUse.holdItem(npc, tool)) {
			return Trip.DONE;
		}

		Household.Stored chest = Household.chestWith(npc, tool);
		if (chest == null) {
			return Trip.FAILED;
		}

		int[] pos = chest.pos();
		Approach.Status status = approach.toward(npc, pos[0], pos[1], pos[2]);
		if (status == Approach.Status.WALKING) {
			return Trip.ON_THE_WAY;
		}

		if (status == Approach.Status.STUCK || Containers.take(npc, chest.container(), tool, 1) == 0) {
			return Trip.FAILED;
		}

		npc.swingArm();
		BlockUse.holdItem(npc, tool);
		return Trip.DONE;
	}

	/** Puts everything matching from the pack into the household's chests. DONE once stored, or if there's nowhere to put it. */
	static Trip storeAway(EntityNPC npc, Approach approach, Predicate<ItemStack> what) {
		if (npc.inventory.findSlot(what) < 0) {
			return Trip.DONE;
		}

		Household.Stored chest = Household.chestWithRoom(npc);
		if (chest == null) {
			return Trip.FAILED;
		}

		int[] pos = chest.pos();
		Approach.Status status = approach.toward(npc, pos[0], pos[1], pos[2]);
		if (status == Approach.Status.WALKING) {
			return Trip.ON_THE_WAY;
		}

		if (status == Approach.Status.STUCK) {
			return Trip.FAILED;
		}

		npc.swingArm();
		Containers.store(npc, chest.container(), what);
		// A chest that filled up partway leaves the rest for the next one.
		return npc.inventory.findSlot(what) >= 0 && Household.chestWithRoom(npc) != null ? Trip.ON_THE_WAY : Trip.DONE;
	}
}
