package lifeblood.travel;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.inventory.NpcInventory;
import lifeblood.work.Approach;
import lifeblood.work.Containers;

/**
 * What a trip takes from home and brings back: food for the road and what the kind of trip needs (torches and a pickaxe
 * for caves, a weapon), taken from the household's chests before setting out; and everything brought back, put away
 * again, but for a little food. Someone with no household just goes with what they carry.
 */
public final class Supplies {
	/** How much food they take for the road, in items. */
	private static final int FOOD_PACKED = 8;
	/** How many of anything else they take, at most per kind of thing. */
	private static final int MOST_OF_A_THING = 24;
	/** How much food they keep on them once home, in items. */
	private static final int FOOD_KEPT = 3;

	private static final Predicate<ItemStack> FOOD = stack -> stack.getItem() instanceof ItemFood;

	private Supplies() {
	}

	/** Packing at home: one chest at a time, food first, then what the trip needs. */
	static final class Packing {
		private final Predicate<ItemStack> needs;
		private final Approach approach = new Approach();
		private boolean foodDone;

		Packing(Predicate<ItemStack> needs) {
			this.needs = needs;
		}

		/** One tick of packing; true once done (or there's no home to pack from). */
		boolean tick(EntityNPC npc) {
			Predicate<ItemStack> wanted = this.foodDone ? this.needs.and(stack -> npc.inventory.findSlot(stack::isItemEqual) < 0) : FOOD;
			Household.Stored chest = Household.chestWith(npc, wanted);
			if (chest == null) {
				if (this.foodDone) {
					return true;
				}

				this.foodDone = true;
				return false;
			}

			int[] pos = chest.pos();
			Approach.Status status = this.approach.toward(npc, pos[0], pos[1], pos[2]);
			if (status == Approach.Status.WALKING) {
				return false;
			}

			if (status == Approach.Status.STUCK) {
				return true;
			}

			npc.swingArm();
			Containers.take(npc, chest.container(), wanted, this.foodDone ? MOST_OF_A_THING : FOOD_PACKED);
			this.foodDone = true;
			return false;
		}
	}

	/** Putting the haul away at home: everything in the pack but a little food, into the household's chests. */
	static final class Unpacking {
		private final Approach approach = new Approach();

		/** One tick of putting things away; true once done (or there's nowhere to put them). */
		boolean tick(EntityNPC npc) {
			Predicate<ItemStack> haul = haul(npc);
			if (npc.inventory.findSlot(haul) < 0) {
				return true;
			}

			Household.Stored chest = Household.chestWithRoom(npc);
			if (chest == null) {
				return true;
			}

			int[] pos = chest.pos();
			Approach.Status status = this.approach.toward(npc, pos[0], pos[1], pos[2]);
			if (status == Approach.Status.WALKING) {
				return false;
			}

			if (status == Approach.Status.REACHED) {
				npc.swingArm();
				Containers.store(npc, chest.container(), haul);
			}

			return true;
		}

		/** Everything carried in the pack (not the hand or armor), except the food they keep. */
		private static Predicate<ItemStack> haul(EntityNPC npc) {
			int food = 0;
			for (int i = 0; i < NpcInventory.PACK_SIZE; i++) {
				ItemStack stack = npc.inventory.getItem(NpcInventory.PACK_START + i);
				if (stack != null && FOOD.test(stack)) {
					food += stack.stackSize;
				}
			}

			boolean spareFood = food > FOOD_KEPT;
			return stack -> !FOOD.test(stack) || spareFood;
		}
	}

	/** How much food they carry, in items. */
	public static int foodCarried(EntityNPC npc) {
		int food = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack != null && FOOD.test(stack)) {
				food += stack.stackSize;
			}
		}

		return food;
	}

	/** How many items matching they carry. */
	public static int count(EntityNPC npc, Predicate<ItemStack> matches) {
		int count = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack != null && matches.test(stack)) {
				count += stack.stackSize;
			}
		}

		return count;
	}
}
