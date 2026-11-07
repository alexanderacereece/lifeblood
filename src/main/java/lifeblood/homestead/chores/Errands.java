package lifeblood.homestead.chores;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.work.Approach;
import lifeblood.work.BlockUse;
import lifeblood.work.Containers;

/**
 * The trips to the house that chores begin and end with: fetching a tool or supplies (seeds) from the household's
 * chests, and putting away what was gathered. Each call is one tick; the Approach walks them there.
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

		Trip trip = fetch(npc, approach, tool, 1);
		if (trip == Trip.DONE) {
			BlockUse.holdItem(npc, tool);
		}

		return trip;
	}

	/** Takes up to this many of something from the household's chests into the pack. FAILED if there's none at home. */
	static Trip fetch(EntityNPC npc, Approach approach, Predicate<ItemStack> what, int most) {
		Household.Stored chest = Household.chestWith(npc, what);
		if (chest == null) {
			return Trip.FAILED;
		}

		int[] pos = chest.pos();
		Approach.Status status = approach.toward(npc, pos[0], pos[1], pos[2]);
		if (status == Approach.Status.WALKING) {
			return Trip.ON_THE_WAY;
		}

		if (status == Approach.Status.STUCK || Containers.take(npc, chest.container(), what, most) == 0) {
			return Trip.FAILED;
		}

		npc.swingArm();
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
