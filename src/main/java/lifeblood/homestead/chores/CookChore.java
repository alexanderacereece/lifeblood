package lifeblood.homestead.chores;

import java.util.function.Predicate;
import net.minecraft.core.block.BlockLogicLog;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.work.Approach;
import lifeblood.work.Smelting;
import org.jetbrains.annotations.Nullable;

/**
 * Cooking for the household, by the game's own recipes: wheat made into dough at the crafting table (three in a row,
 * as the game's recipe has it), then raw meat, fish and dough taken to the furnace with fuel, and whatever the
 * furnace has finished taken out. The furnace cooks at its own pace; what's done is collected next time. Cooked food
 * goes into the chests.
 */
public final class CookChore implements Chore {
	/** How much wheat one dough takes, and how many doughs one trip makes at most. */
	private static final int WHEAT_PER_DOUGH = 3;
	private static final int DOUGH_PER_TRIP = 4;
	/** How much to cook, and how much fuel to bring, per trip. */
	private static final int COOK_PER_TRIP = 8;
	private static final int FUEL_PER_TRIP = 4;

	/** Food the furnace makes something of: raw meat, raw fish, dough. */
	static final Predicate<ItemStack> COOKABLE = stack -> {
		ItemStack result = Smelting.resultOf(stack);
		return result != null && result.getItem() instanceof ItemFood;
	};
	private static final Predicate<ItemStack> WHEAT = stack -> stack.getItem() == Items.WHEAT;
	private static final Predicate<ItemStack> FUEL = stack -> stack.getItem() == Items.COAL
		|| stack.getItem() instanceof ItemBlock<?> block && block.getBlock().getLogic() instanceof BlockLogicLog;
	/** What goes back to the chests: cooked food, and anything left over. */
	private static final Predicate<ItemStack> HAUL = stack -> stack.getItem() instanceof ItemFood && !COOKABLE.test(stack) && !stack.getItem().getKey().contains("raw")
		|| COOKABLE.test(stack) || WHEAT.test(stack) || FUEL.test(stack);

	private enum Step {
		FETCH_WHEAT, KNEAD, FETCH_FOOD, FETCH_FUEL, FURNACE, STORE
	}

	private final Approach approach = new Approach();
	private Step step = Step.FETCH_WHEAT;

	/** Whether there's cooking to do: something to cook in the chests, or something done in a furnace. */
	public static boolean hasWork(EntityNPC npc) {
		HomesteadRegister.Homestead house = Household.of(npc);
		if (house == null || house.furnaces().isEmpty()) {
			return false;
		}

		if (Household.count(npc, COOKABLE) > 0 || Household.count(npc, WHEAT) >= WHEAT_PER_DOUGH && !house.workbenches().isEmpty()) {
			return true;
		}

		Container furnace = furnace(npc, house);
		return furnace != null && furnace.getItem(Smelting.OUTPUT_SLOT) != null;
	}

	@Override
	public boolean tick(EntityNPC npc) {
		HomesteadRegister.Homestead house = Household.of(npc);
		if (house == null) {
			return true;
		}

		switch (this.step) {
			case FETCH_WHEAT -> {
				boolean canKnead = !house.workbenches().isEmpty() && Household.count(npc, WHEAT) >= WHEAT_PER_DOUGH;
				if (!canKnead || Errands.fetch(npc, this.approach, WHEAT, WHEAT_PER_DOUGH * DOUGH_PER_TRIP) != Errands.Trip.ON_THE_WAY) {
					this.step = canKnead ? Step.KNEAD : Step.FETCH_FOOD;
				}
			}
			case KNEAD -> {
				int[] bench = house.workbenches().get(0);
				Approach.Status status = this.approach.toward(npc, bench[0], bench[1], bench[2]);
				if (status == Approach.Status.REACHED) {
					knead(npc);
				}

				if (status != Approach.Status.WALKING) {
					this.step = Step.FETCH_FOOD;
				}
			}
			case FETCH_FOOD -> {
				if (Errands.fetch(npc, this.approach, COOKABLE, COOK_PER_TRIP) != Errands.Trip.ON_THE_WAY) {
					this.step = Step.FETCH_FUEL;
				}
			}
			case FETCH_FUEL -> {
				if (npc.inventory.findSlot(FUEL) >= 0 || Errands.fetch(npc, this.approach, FUEL, FUEL_PER_TRIP) != Errands.Trip.ON_THE_WAY) {
					this.step = Step.FURNACE;
				}
			}
			case FURNACE -> this.tendFurnace(npc, house);
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	/** Makes dough from wheat at the crafting table: three wheat in a row make one, the game's own recipe. */
	private static void knead(EntityNPC npc) {
		npc.swingArm();
		int wheat = 0;
		for (int slot = 0; slot < npc.inventory.getContainerSize(); slot++) {
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack != null && WHEAT.test(stack)) {
				wheat += stack.stackSize;
			}
		}

		for (int dough = 0; dough < Math.min(DOUGH_PER_TRIP, wheat / WHEAT_PER_DOUGH); dough++) {
			for (int i = 0; i < WHEAT_PER_DOUGH; i++) {
				npc.inventory.consumeOne(WHEAT);
			}

			npc.inventory.addToPack(new ItemStack(Items.DOUGH, 1));
		}
	}

	/** At the furnace: takes out what's done, puts in what's to cook, and adds fuel if it's out. */
	private void tendFurnace(EntityNPC npc, HomesteadRegister.Homestead house) {
		int[] pos = house.furnaces().get(0);
		Approach.Status status = this.approach.toward(npc, pos[0], pos[1], pos[2]);
		if (status == Approach.Status.WALKING) {
			return;
		}

		this.step = Step.STORE;
		Container furnace = furnace(npc, house);
		if (status != Approach.Status.REACHED || furnace == null) {
			return;
		}

		npc.swingArm();
		ItemStack done = furnace.getItem(Smelting.OUTPUT_SLOT);
		if (done != null && npc.inventory.addToPack(done)) {
			furnace.setItem(Smelting.OUTPUT_SLOT, null);
		}

		putIn(npc, furnace, Smelting.INPUT_SLOT, COOKABLE);
		putIn(npc, furnace, Smelting.FUEL_SLOT, FUEL);
		furnace.setChanged();
	}

	/** Moves a stack matching into a furnace slot: into an empty slot, or onto the same thing already there. */
	private static void putIn(EntityNPC npc, Container furnace, int slot, Predicate<ItemStack> what) {
		ItemStack there = furnace.getItem(slot);
		int from = npc.inventory.findSlot(stack -> what.test(stack) && (there == null || there.canStackWith(stack)));
		if (from < 0) {
			return;
		}

		ItemStack stack = npc.inventory.getItem(from);
		if (there == null) {
			furnace.setItem(slot, stack);
			npc.inventory.setItem(from, null);
			return;
		}

		int room = Math.min(there.getMaxStackSize(), furnace.getMaxStackSize()) - there.stackSize;
		int moved = Math.min(room, stack.stackSize);
		there.stackSize += moved;
		stack.stackSize -= moved;
		npc.inventory.setItem(from, stack.stackSize > 0 ? stack : null);
	}

	/** The household's first furnace, if it's loaded. */
	@Nullable
	private static Container furnace(EntityNPC npc, HomesteadRegister.Homestead house) {
		if (house.furnaces().isEmpty()) {
			return null;
		}

		int[] pos = house.furnaces().get(0);
		TilePos tile = new TilePos(pos[0], pos[1], pos[2]);
		return npc.world.isBlockLoaded(tile) && npc.world.getTileEntity(tile) instanceof Container container ? container : null;
	}

	@Override
	public String doing() {
		return "cooking";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_WHEAT -> "fetching wheat";
			case KNEAD -> "making dough";
			case FETCH_FOOD, FETCH_FUEL -> "getting food to cook";
			case FURNACE -> "cooking";
			case STORE -> "putting food away";
		};
	}
}
