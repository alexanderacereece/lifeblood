package lifeblood.homestead.life;

import java.util.function.Predicate;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.work.Approach;
import lifeblood.work.Fishing;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Fishing: takes a rod from home, finds open water near home with a bank to stand on, fishes by the player's rules
 * (see work/Fishing) until they've caught a few or had enough, and brings the fish home.
 */
public final class FishChore implements Chore {
	/** How far from home they look for water, in blocks. */
	private static final int SEARCH_RADIUS = 32;
	/** How many fish make a good day, and the longest they sit at it, in ticks (two in-game hours). */
	private static final int FISH_WANTED = 4;
	private static final int MAX_FISHING_TICKS = 2000;

	private static final Predicate<ItemStack> ROD = Fishing::isRod;
	private static final Predicate<ItemStack> HAUL = stack -> ROD.test(stack) || stack.getItem() == Items.FOOD_FISH_RAW;

	private enum Step {
		FETCH_ROD, FIND_WATER, FISH, STORE
	}

	private final Approach approach = new Approach();
	private final Fishing fishing;
	private Step step = Step.FETCH_ROD;
	@Nullable
	private int[] water;
	private int caught;
	private long started = -1L;

	public FishChore(EntityNPC npc) {
		this.fishing = new Fishing(npc);
	}

	/** Whether they could go fishing at all: a rod at home or on them. */
	public static boolean canFish(EntityNPC npc) {
		return npc.inventory.findSlot(ROD) >= 0 || Household.count(npc, ROD) > 0;
	}

	@Override
	public boolean tick(EntityNPC npc) {
		switch (this.step) {
			case FETCH_ROD -> {
				Errands.Trip trip = Errands.fetchTool(npc, this.approach, ROD);
				if (trip == Errands.Trip.FAILED) {
					return true;
				}

				if (trip == Errands.Trip.DONE) {
					this.step = Step.FIND_WATER;
				}
			}
			case FIND_WATER -> {
				this.water = findWater(npc);
				this.step = this.water != null ? Step.FISH : Step.STORE;
			}
			case FISH -> this.fish(npc);
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	private void fish(EntityNPC npc) {
		int[] water = this.water;
		ItemPickup.pickUpNearby(npc);
		if (water == null) {
			this.step = Step.STORE;
			return;
		}

		Approach.Status status = this.approach.toward(npc, water[0], water[1], water[2]);
		if (status == Approach.Status.STUCK) {
			this.step = Step.STORE;
			return;
		}

		if (status != Approach.Status.REACHED) {
			return;
		}

		if (this.started < 0L) {
			this.started = npc.world.getWorldTime();
		}

		if (this.fishing.fish(water[0], water[1], water[2])) {
			this.caught++;
			npc.voice.bark("chore_caught_fish", null, null, false);
		}

		boolean noRod = npc.getHeldItem() == null || !ROD.test(npc.getHeldItem());
		if (noRod || this.caught >= FISH_WANTED || npc.world.getWorldTime() - this.started > MAX_FISHING_TICKS) {
			this.fishing.stop();
			this.step = Step.STORE;
		}
	}

	/** The nearest open water near home with dry ground beside it to stand on, or null. */
	@Nullable
	private static int[] findWater(EntityNPC npc) {
		World world = npc.world;
		int homeX = (int) Math.floor(npc.home.getX());
		int homeZ = (int) Math.floor(npc.home.getZ());
		for (int ring = 1; ring <= SEARCH_RADIUS; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}

					int x = homeX + dx;
					int z = homeZ + dz;
					if (!world.isBlockLoaded(new TilePos(x, 0, z))) {
						continue;
					}

					int y = world.getHeightValue(x, z) - 1;
					if (Fishing.isFishable(world, x, y, z) && hasBank(world, x, y, z)) {
						return new int[] {x, y, z};
					}
				}
			}
		}

		return null;
	}

	/** Dry, solid ground beside the water at its level or one above, with room to stand. */
	private static boolean hasBank(World world, int x, int y, int z) {
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		TilePos pos = new TilePos();
		for (int[] side : sides) {
			for (int up = 0; up <= 1; up++) {
				boolean ground = world.getBlockType(pos.set(x + side[0], y + up, z + side[1])).getMaterial().isSolidBlocking();
				boolean room = world.getBlockType(pos.set(x + side[0], y + up + 1, z + side[1])).getMaterial() == Materials.AIR
					&& world.getBlockType(pos.set(x + side[0], y + up + 2, z + side[1])).getMaterial() == Materials.AIR;
				if (ground && room) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public void stop(EntityNPC npc) {
		this.fishing.stop();
		npc.navigator.stop();
	}

	@Override
	public String doing() {
		return "fishing";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_ROD -> "fetching a fishing rod";
			case FIND_WATER -> "looking for water";
			case FISH -> "fishing";
			case STORE -> "bringing fish home";
		};
	}
}
