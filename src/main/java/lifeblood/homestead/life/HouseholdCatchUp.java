package lifeblood.homestead.life;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.block.BlockLogicLog;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.block.ItemBlock;
import net.minecraft.core.item.tool.ItemToolAxe;
import net.minecraft.core.item.tool.ItemToolSword;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.building.LocalMaterials;
import lifeblood.building.WoodType;
import lifeblood.entity.EntityNPC;
import lifeblood.event.LifeEvents;
import lifeblood.event.TimeAwayEvent;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.ownership.BlockOwners;
import lifeblood.ownership.Ownership;
import lifeblood.personality.Quirk;
import lifeblood.work.Containers;
import lifeblood.work.Smelting;
import lifeblood.world.WorldSystem;
import org.joml.primitives.AABBd;

/**
 * Homesteads living while unloaded, as a ledger (see docs/unloaded.md): a household nobody was near doesn't tick, so
 * when its house is loaded again after a while, it catches up on the time at once, at rates measured from the chores
 * themselves. Their field ripens and is harvested and sown again; wood, fish and game come in by the tools they keep;
 * the furnace cooks what's raw and bakes dough into bread, burning fuel; a little food is eaten. Then a TimeAwayEvent lets
 * other systems add the trips taken meanwhile. Everything goes into the chests, as far as they hold it.
 */
public final class HouseholdCatchUp {
	/** How often loaded houses are checked, in ticks. */
	private static final int INTERVAL = 200;
	/** Unwatched for longer than this, a house catches up when it's loaded again, in ticks (two in-game hours). */
	private static final long CATCH_UP_AFTER = 2000L;
	/** The most time caught up at once, in in-game days: past that, a house just picks up where it was. */
	private static final double MOST_DAYS = 28.0;
	private static final long TICKS_PER_DAY = 24000L;
	/** How often wheat comes ripe, in in-game days; a plot gives a wheat and back its seed and a spare each time. */
	private static final double DAYS_PER_HARVEST = 2.0;
	/** What a day of chores brings in, with the tools for it: logs for a household with an axe, fish with a rod, meat with a weapon. */
	private static final double LOGS_PER_DAY = 6.0;
	private static final double FISH_PER_DAY = 1.5;
	private static final double MEAT_PER_DAY = 0.8;
	/** Food eaten per person per day, in items (they eat to heal, so not much). */
	private static final double EATEN_PER_DAY = 0.3;
	/** How far around the house its field and people are found, in blocks. */
	private static final int AROUND = 24;
	private static final int LOGS_KEPT = 64;

	public static final WorldSystem SYSTEM = WorldSystem.every(INTERVAL, HouseholdCatchUp::tick);

	private HouseholdCatchUp() {
	}

	private static void tick(World world) {
		if (world.isClientSide) {
			return;
		}

		HomesteadRegister register = HomesteadRegister.get(world);
		long now = world.getWorldTime();
		for (HomesteadRegister.Homestead house : register.all()) {
			TilePos center = new TilePos(house.x() + house.width() / 2, house.y(), house.z() + house.depth() / 2);
			if (!world.isBlockLoaded(center)) {
				continue;
			}

			long last = register.lastLived(house);
			register.setLived(house, now);
			if (last >= 0L && now - last > CATCH_UP_AFTER) {
				catchUp(world, house, Math.min(now - last, (long) (MOST_DAYS * TICKS_PER_DAY)));
			}
		}
	}

	/** Catches a household up on this much time unwatched. */
	static void catchUp(World world, HomesteadRegister.Homestead house, long ticks) {
		List<EntityNPC> people = people(world, house);
		if (people.isEmpty()) {
			return;
		}

		List<Household.Stored> chests = Household.chests(people.get(0), house);
		if (chests.isEmpty()) {
			return;
		}

		Random random = world.rand;
		double days = ticks / (double) TICKS_PER_DAY;
		List<ItemStack> made = new ArrayList<>();
		int harvests = (int) Math.floor(days / DAYS_PER_HARVEST);
		int plots = ripenField(world, house, harvests > 0);
		if (plots > 0 && harvests > 0) {
			made.add(new ItemStack(Items.WHEAT, plots * harvests));
			made.add(new ItemStack(Items.SEEDS_WHEAT, Math.min(64, plots * harvests / 2)));
		}

		boolean grownUps = people.stream().anyMatch(npc -> !npc.life.isChild());
		if (grownUps && has(chests, people, stack -> stack.getItem() instanceof ItemToolAxe)) {
			int logs = Math.min(roll(LOGS_PER_DAY * days, random), Math.max(0, LOGS_KEPT - count(chests, HouseholdCatchUp::isLog)));
			if (logs > 0) {
				// The trees that grow around the house.
				WoodType wood = LocalMaterials.commonestWood(world, house.x(), house.y(), house.z());
				made.add(new ItemStack(wood != null ? wood.log : Blocks.LOG_OAK, logs));
			}
		}

		if (has(chests, people, stack -> stack.getItem() == Items.TOOL_FISHINGROD)) {
			made.add(new ItemStack(Items.FOOD_FISH_RAW, roll(FISH_PER_DAY * days, random)));
		}

		boolean hunters = people.stream().anyMatch(npc -> !npc.life.isChild() && !npc.personality.has(Quirk.ANIMAL_LOVER));
		if (hunters && has(chests, people, stack -> stack.getItem() instanceof ItemToolSword || stack.getItem() == Items.TOOL_BOW)) {
			made.add(new ItemStack(random.nextBoolean() ? Items.FOOD_PORKCHOP_RAW : Items.FOOD_VENISON_RAW, roll(MEAT_PER_DAY * days, random)));
		}

		for (ItemStack stack : made) {
			if (stack.stackSize > 0) {
				deposit(world, house, chests, stack);
			}
		}

		cook(world, house, chests);
		eat(chests, roll(EATEN_PER_DAY * days * people.size(), random));
		LifeEvents.post(new TimeAwayEvent(world, people, ticks, stack -> deposit(world, house, chests, stack)));
	}

	/** The household's people who are loaded: everyone whose home is in the house. */
	private static List<EntityNPC> people(World world, HomesteadRegister.Homestead house) {
		AABBd around = new AABBd(house.x() - AROUND, house.y() - AROUND, house.z() - AROUND, house.x() + house.width() + AROUND,
			house.y() + house.height() + AROUND, house.z() + house.depth() + AROUND);
		List<EntityNPC> people = new ArrayList<>();
		for (EntityNPC npc : world.getEntitiesWithinAABB(EntityNPC.class, around)) {
			if (npc.isAlive() && house.contains(npc.home.getX(), npc.home.getY(), npc.home.getZ())) {
				people.add(npc);
			}
		}

		return people;
	}

	/**
	 * Brings the household's wheat on: ripe if a harvest's time has passed (then it's taken as harvested and sown again,
	 * so it's left young), else a little grown. Returns how many plots the field has.
	 */
	private static int ripenField(World world, HomesteadRegister.Homestead house, boolean harvested) {
		int plots = 0;
		TilePos pos = new TilePos();
		for (int x = house.x() - AROUND; x <= house.x() + house.width() + AROUND; x++) {
			for (int z = house.z() - AROUND; z <= house.z() + house.depth() + AROUND; z++) {
				for (int y = house.y() - 5; y <= house.y() + 5; y++) {
					if (world.getBlockType(pos.set(x, y, z)) != Blocks.FARMLAND_DIRT) {
						continue;
					}

					UUID owner = BlockOwners.ownerAt(world, x, y, z);
					if (owner == null || !Ownership.shares(world, owner, house.head()) && !owner.equals(house.head())) {
						continue;
					}

					plots++;
					if (world.getBlockType(pos.set(x, y + 1, z)) == Blocks.CROPS_WHEAT) {
						// Harvested and sown again since: young wheat; otherwise it's had time to grow most of the way.
						world.setBlockDataNotify(pos, harvested ? world.rand.nextInt(3) : Math.min(7, world.getBlockData(pos) + 4));
					}
				}
			}
		}

		return plots;
	}

	/** The furnace's work: raw food cooked and dough baked, as far as the fuel at home goes. */
	private static void cook(World world, HomesteadRegister.Homestead house, List<Household.Stored> chests) {
		if (house.furnaces().isEmpty()) {
			return;
		}

		// Wheat beyond a little seed corn becomes dough, then bread (three wheat a dough, the game's recipe).
		int wheat = count(chests, stack -> stack.getItem() == Items.WHEAT);
		int doughs = house.workbenches().isEmpty() ? 0 : Math.max(0, wheat - 6) / 3;
		take(chests, stack -> stack.getItem() == Items.WHEAT, doughs * 3);
		List<ItemStack> toCook = new ArrayList<>();
		if (doughs > 0) {
			toCook.add(new ItemStack(Items.DOUGH, doughs));
		}

		for (Household.Stored chest : chests) {
			for (int slot = 0; slot < chest.container().getContainerSize(); slot++) {
				ItemStack stack = chest.container().getItem(slot);
				ItemStack result = stack != null ? Smelting.resultOf(stack) : null;
				if (result != null && result.getItem() instanceof ItemFood) {
					toCook.add(stack);
					chest.container().setItem(slot, null);
				}
			}
		}

		// A coal cooks eight, a log about one and a half, as in the furnace.
		int items = toCook.stream().mapToInt(stack -> stack.stackSize).sum();
		int fromCoal = take(chests, stack -> stack.getItem() == Items.COAL, (items + 7) / 8) * 8;
		int fromLogs = take(chests, HouseholdCatchUp::isLog, (int) Math.ceil(Math.max(0, items - fromCoal) / 1.5)) * 3 / 2;
		int cookable = Math.min(items, fromCoal + fromLogs);
		for (ItemStack stack : toCook) {
			ItemStack result = Smelting.resultOf(stack);
			int cooked = Math.min(stack.stackSize, cookable);
			cookable -= cooked;
			if (cooked > 0 && result != null) {
				deposit(world, house, chests, new ItemStack(result.getItem(), cooked, result.getMetadata()));
			}

			if (stack.stackSize > cooked) {
				ItemStack left = stack.copy();
				left.stackSize = stack.stackSize - cooked;
				deposit(world, house, chests, left);
			}
		}
	}

	/** Food eaten meanwhile, from the chests. */
	private static void eat(List<Household.Stored> chests, int items) {
		take(chests, stack -> stack.getItem() instanceof ItemFood, items);
	}

	/** Puts something into the household's chests; what doesn't fit is left by the house. */
	static void deposit(World world, HomesteadRegister.Homestead house, List<Household.Stored> chests, ItemStack stack) {
		for (Household.Stored chest : chests) {
			Containers.insert(chest.container(), stack);
			chest.container().setChanged();
			if (stack.stackSize <= 0) {
				return;
			}
		}

		world.entityJoinedWorld(new EntityItem(world, house.x() + house.width() / 2.0, house.y() + 1.5, house.z() + house.depth() / 2.0, stack));
	}

	private static boolean has(List<Household.Stored> chests, List<EntityNPC> people, Predicate<ItemStack> tool) {
		if (count(chests, tool) > 0) {
			return true;
		}

		for (EntityNPC npc : people) {
			if (npc.inventory.findSlot(tool) >= 0) {
				return true;
			}
		}

		return false;
	}

	private static int count(List<Household.Stored> chests, Predicate<ItemStack> what) {
		int count = 0;
		for (Household.Stored chest : chests) {
			count += Containers.count(chest.container(), what);
		}

		return count;
	}

	/** Takes up to this many matching items out of the chests; returns how many it took. */
	private static int take(List<Household.Stored> chests, Predicate<ItemStack> what, int most) {
		int taken = 0;
		for (Household.Stored chest : chests) {
			Container container = chest.container();
			for (int slot = 0; slot < container.getContainerSize() && taken < most; slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack != null && what.test(stack)) {
					int amount = Math.min(stack.stackSize, most - taken);
					stack.stackSize -= amount;
					taken += amount;
					container.setItem(slot, stack.stackSize > 0 ? stack : null);
				}
			}

			container.setChanged();
		}

		return taken;
	}

	private static boolean isLog(ItemStack stack) {
		return stack.getItem() instanceof ItemBlock<?> block && block.getBlock().getLogic() instanceof BlockLogicLog;
	}

	/** A whole number near an expected amount: the fraction rounds up as often as it's worth. */
	private static int roll(double expected, Random random) {
		int whole = (int) Math.floor(expected);
		return whole + (random.nextDouble() < expected - whole ? 1 : 0);
	}
}
