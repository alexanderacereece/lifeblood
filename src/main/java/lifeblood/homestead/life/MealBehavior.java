package lifeblood.homestead.life;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import lifeblood.ai.NpcBehavior;
import lifeblood.ai.behavior.WanderBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.work.Approach;
import lifeblood.work.Containers;

/**
 * Mealtime at home: back to the house, where someone carrying little food takes some from the household's chests, so
 * they have it when they need it (they eat by the player's rules, when hurt; see NpcEating). Then they stay around the
 * house until the meal hour is over.
 */
public final class MealBehavior implements NpcBehavior {
	public static final String ID = "meal";
	/** How much food someone keeps on them, in items. */
	private static final int FOOD_CARRIED = 3;
	/** How far from home they potter about once they've eaten, in blocks. */
	private static final int AROUND_HOME = 4;
	private static final Predicate<ItemStack> FOOD = stack -> stack.getItem() instanceof ItemFood;

	private final Approach approach = new Approach();
	private final WanderBehavior pottering = new WanderBehavior(AROUND_HOME);
	private boolean fetched;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void tick(EntityNPC npc) {
		if (!this.fetched) {
			this.fetched = this.fetchFood(npc);
			if (!this.fetched) {
				return;
			}
		}

		this.pottering.tick(npc);
	}

	/** Takes food from a chest if they carry too little; returns true once done (or there's nothing to fetch). */
	private boolean fetchFood(EntityNPC npc) {
		int carried = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack != null && FOOD.test(stack)) {
				carried += stack.stackSize;
			}
		}

		Household.Stored chest = carried >= FOOD_CARRIED ? null : Household.chestWith(npc, FOOD);
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
			Containers.take(npc, chest.container(), FOOD, FOOD_CARRIED - carried);
		}

		return true;
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public String describe() {
		return this.fetched ? "home for a meal" : "fetching food from home";
	}
}
