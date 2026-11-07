package lifeblood.homestead.chores;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.work.Approach;

/**
 * Making arrows at the crafting table for a household that hunts with a bow, by the game's recipe: a flint, a stick and
 * a feather make four. Flint comes from digging gravel (GravelChore), feathers from chickens, sticks from wood.
 */
public final class FletchChore implements Chore {
	/** How many arrows a household with a bow likes to keep. */
	static final int ARROWS_WANTED = 24;
	/** The game's recipe: one of each makes four arrows. How many to make at most per trip. */
	private static final int ARROWS_PER_CRAFT = 4;
	private static final int CRAFTS_PER_TRIP = 4;

	private static final Predicate<ItemStack> BOW = stack -> stack.getItem() == Items.TOOL_BOW;
	static final Predicate<ItemStack> FLINT = stack -> stack.getItem() == Items.FLINT;
	private static final Predicate<ItemStack> STICK = stack -> stack.getItem() == Items.STICK;
	private static final Predicate<ItemStack> FEATHER = stack -> stack.getItem() == Items.FEATHER_CHICKEN;
	private static final Predicate<ItemStack> HAUL = stack -> HuntChore.ARROWS.test(stack) || FLINT.test(stack) || STICK.test(stack) || FEATHER.test(stack);

	private enum Step {
		FETCH_FLINT, FETCH_STICKS, FETCH_FEATHERS, CRAFT, STORE
	}

	private final Approach approach = new Approach();
	private Step step = Step.FETCH_FLINT;

	/** Whether a household that hunts with a bow is short of arrows. */
	static boolean wantsArrows(EntityNPC npc) {
		boolean bow = Household.count(npc, BOW) > 0 || npc.inventory.findSlot(BOW) >= 0;
		return bow && Household.count(npc, HuntChore.ARROWS) < ARROWS_WANTED;
	}

	/** Whether arrows could be made now: a crafting table, and flint, sticks and feathers at home. */
	public static boolean canFletch(EntityNPC npc) {
		HomesteadRegister.Homestead house = Household.of(npc);
		return house != null && !house.workbenches().isEmpty() && wantsArrows(npc)
			&& Household.count(npc, FLINT) > 0 && Household.count(npc, STICK) > 0 && Household.count(npc, FEATHER) > 0;
	}

	@Override
	public boolean tick(EntityNPC npc) {
		HomesteadRegister.Homestead house = Household.of(npc);
		if (house == null || house.workbenches().isEmpty()) {
			return true;
		}

		switch (this.step) {
			case FETCH_FLINT -> this.fetch(npc, FLINT, Step.FETCH_STICKS);
			case FETCH_STICKS -> this.fetch(npc, STICK, Step.FETCH_FEATHERS);
			case FETCH_FEATHERS -> this.fetch(npc, FEATHER, Step.CRAFT);
			case CRAFT -> {
				int[] bench = house.workbenches().get(0);
				Approach.Status status = this.approach.toward(npc, bench[0], bench[1], bench[2]);
				if (status == Approach.Status.REACHED) {
					craft(npc);
				}

				if (status != Approach.Status.WALKING) {
					this.step = Step.STORE;
				}
			}
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	private void fetch(EntityNPC npc, Predicate<ItemStack> what, Step next) {
		if (npc.inventory.findSlot(what) >= 0 || Errands.fetch(npc, this.approach, what, CRAFTS_PER_TRIP) != Errands.Trip.ON_THE_WAY) {
			this.step = next;
		}
	}

	/** One flint, one stick and one feather for each four arrows, as many times as they have all three. */
	private static void craft(EntityNPC npc) {
		npc.swingArm();
		for (int craft = 0; craft < CRAFTS_PER_TRIP; craft++) {
			if (npc.inventory.findSlot(FLINT) < 0 || npc.inventory.findSlot(STICK) < 0 || npc.inventory.findSlot(FEATHER) < 0) {
				return;
			}

			npc.inventory.consumeOne(FLINT);
			npc.inventory.consumeOne(STICK);
			npc.inventory.consumeOne(FEATHER);
			npc.inventory.addToPack(new ItemStack(Items.AMMO_ARROW, ARROWS_PER_CRAFT));
		}
	}

	@Override
	public String doing() {
		return "fletching";
	}

	@Override
	public String describe() {
		return this.step == Step.CRAFT ? "making arrows" : this.step == Step.STORE ? "putting arrows away" : "fetching what arrows take";
	}
}
