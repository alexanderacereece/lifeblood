package lifeblood.homestead.chores;

import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.item.tool.ItemToolHoe;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.life.Household;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.ownership.BlockOwners;
import lifeblood.ownership.Ownership;
import lifeblood.work.Approach;
import lifeblood.work.BlockBreaking;
import lifeblood.work.BlockUse;
import lifeblood.work.ItemPickup;
import org.jetbrains.annotations.Nullable;

/**
 * Farming the household's own field: takes the hoe and seeds from home, harvests ripe wheat, sows any bare farmland,
 * and tills a few new plots until the field is big enough, starting it beside water if there's some near so it stays
 * wet. Everything by the player's rules: a hoe only tills grass and dirt, seeds only take on farmland. The household's
 * field is the farmland its people tilled. The harvest goes home.
 */
public final class FarmChore implements Chore {
	/** How far from home the field may be, in blocks. */
	private static final int FIELD_RADIUS = 20;
	/** How far above and below home's level the field may be, in blocks. */
	private static final int FIELD_HEIGHT = 4;
	/** How big the field grows, in plots of farmland. */
	private static final int FIELD_SIZE = 16;
	/** How many new plots one trip tills at most. */
	private static final int TILLED_PER_TRIP = 4;
	/** How many plots one trip works (harvests, sows or tills) at most before heading home. */
	private static final int PLOTS_PER_TRIP = 24;
	/** How close to water a new field starts, so the farmland stays wet (the game's own reach for water), in blocks. */
	private static final int WATER_REACH = 4;
	/** Plots this close to the house are left alone, so doors and paths stay clear, in blocks. */
	private static final int HOUSE_CLEARANCE = 2;
	/** The growth stage of ripe wheat. */
	private static final int RIPE = 7;

	private static final Predicate<ItemStack> HOE = stack -> stack.getItem() instanceof ItemToolHoe;
	private static final Predicate<ItemStack> SEEDS = stack -> stack.getItem() == Items.SEEDS_WHEAT;
	/** What goes home afterwards: the harvest, spare seeds and the hoe. */
	private static final Predicate<ItemStack> HAUL = stack -> HOE.test(stack) || SEEDS.test(stack) || stack.getItem() == Items.WHEAT;

	private enum Step {
		FETCH_HOE, FETCH_SEEDS, WORK, STORE
	}

	/** A plot to work, and what to do there. */
	private record Plot(int x, int y, int z, Task task) {
	}

	private enum Task {
		HARVEST, SOW, TILL
	}

	private final Approach approach = new Approach();
	private final BlockBreaking breaking;
	private Step step = Step.FETCH_HOE;
	private boolean hasHoe;
	@Nullable
	private Plot plot;
	private int worked;
	private int tilled;
	private int harvested;

	public FarmChore(EntityNPC npc) {
		this.breaking = new BlockBreaking(npc);
	}

	@Override
	public boolean tick(EntityNPC npc) {
		switch (this.step) {
			case FETCH_HOE -> {
				Errands.Trip trip = Errands.fetchTool(npc, this.approach, HOE);
				if (trip != Errands.Trip.ON_THE_WAY) {
					this.hasHoe = trip == Errands.Trip.DONE;
					this.step = Step.FETCH_SEEDS;
				}
			}
			case FETCH_SEEDS -> {
				if (npc.inventory.findSlot(SEEDS) >= 0 || Errands.fetch(npc, this.approach, SEEDS, 32) != Errands.Trip.ON_THE_WAY) {
					this.step = Step.WORK;
				}
			}
			case WORK -> this.work(npc);
			case STORE -> {
				return Errands.storeAway(npc, this.approach, HAUL) != Errands.Trip.ON_THE_WAY;
			}
		}

		return false;
	}

	/** Works one plot after another: what's ripe first, then what's bare, then new ground. */
	private void work(EntityNPC npc) {
		ItemPickup.pickUpNearby(npc);
		if (this.plot == null) {
			this.plot = this.worked < PLOTS_PER_TRIP ? this.nextPlot(npc) : null;
			if (this.plot == null) {
				this.step = Step.STORE;
				return;
			}
		}

		Plot plot = this.plot;
		int targetY = plot.task() == Task.HARVEST ? plot.y() + 1 : plot.y();
		Approach.Status status = this.approach.toward(npc, plot.x(), targetY, plot.z());
		if (status == Approach.Status.WALKING) {
			return;
		}

		if (status == Approach.Status.REACHED) {
			switch (plot.task()) {
				case HARVEST -> {
					if (this.breaking.dig(plot.x(), plot.y() + 1, plot.z()) == BlockBreaking.Result.DIGGING) {
						return;
					}

					if (this.harvested++ == 0) {
						npc.voice.bark("chore_harvest", null, null, false);
					}
				}
				case SOW -> {
					if (BlockUse.holdItem(npc, SEEDS)) {
						BlockUse.useOnTop(npc, plot.x(), plot.y(), plot.z());
					}
				}
				case TILL -> {
					// Tall grass or a flower in the way comes off first, as a player knocks it off before tilling.
					if (npc.world.getBlockType(new TilePos(plot.x(), plot.y() + 1, plot.z())) != Blocks.AIR) {
						if (this.breaking.dig(plot.x(), plot.y() + 1, plot.z()) == BlockBreaking.Result.DIGGING) {
							return;
						}
					}

					if (BlockUse.holdItem(npc, HOE) && BlockUse.till(npc, plot.x(), plot.y(), plot.z())) {
						this.tilled++;
					}
				}
			}
		}

		this.worked++;
		this.plot = null;
	}

	/** The nearest plot needing work, by what matters most; null if the field's all done for now. */
	@Nullable
	private Plot nextPlot(EntityNPC npc) {
		World world = npc.world;
		HomesteadRegister.Homestead house = Household.of(npc);
		int homeX = (int) Math.floor(npc.home.getX());
		int homeY = (int) Math.floor(npc.home.getY());
		int homeZ = (int) Math.floor(npc.home.getZ());
		Plot ripe = null;
		Plot bare = null;
		Plot fresh = null;
		Plot byWater = null;
		int fieldSize = 0;
		double ripeDistance = Double.MAX_VALUE;
		double bareDistance = Double.MAX_VALUE;
		double freshDistance = Double.MAX_VALUE;
		double waterDistance = Double.MAX_VALUE;
		boolean seeds = npc.inventory.findSlot(SEEDS) >= 0;
		TilePos pos = new TilePos();
		for (int x = homeX - FIELD_RADIUS; x <= homeX + FIELD_RADIUS; x++) {
			for (int z = homeZ - FIELD_RADIUS; z <= homeZ + FIELD_RADIUS; z++) {
				if (!world.isBlockLoaded(pos.set(x, homeY, z)) || nearHouse(house, x, z)) {
					continue;
				}

				for (int y = homeY - FIELD_HEIGHT; y <= homeY + FIELD_HEIGHT; y++) {
					Block<?> block = world.getBlockType(pos.set(x, y, z));
					double distance = npc.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
					if (block == Blocks.FARMLAND_DIRT && this.ours(npc, x, y, z)) {
						fieldSize++;
						Block<?> above = world.getBlockType(pos.set(x, y + 1, z));
						if (above == Blocks.CROPS_WHEAT && world.getBlockData(pos) >= RIPE && distance < ripeDistance) {
							ripe = new Plot(x, y, z, Task.HARVEST);
							ripeDistance = distance;
						} else if (above == Blocks.AIR && seeds && distance < bareDistance) {
							bare = new Plot(x, y, z, Task.SOW);
							bareDistance = distance;
						}

						continue;
					}

					if (!this.hasHoe || this.tilled >= TILLED_PER_TRIP || !tillable(world, x, y, z)) {
						continue;
					}

					if (this.nextToField(npc, x, y, z) && distance < freshDistance) {
						fresh = new Plot(x, y, z, Task.TILL);
						freshDistance = distance;
					} else if (nearWater(world, x, y, z) && distance < waterDistance) {
						byWater = new Plot(x, y, z, Task.TILL);
						waterDistance = distance;
					}
				}
			}
		}

		if (ripe != null) {
			return ripe;
		} else if (bare != null) {
			return bare;
		} else if (fieldSize >= FIELD_SIZE || !this.hasHoe || this.tilled >= TILLED_PER_TRIP) {
			return null;
		} else if (fresh != null) {
			return fresh;
		}

		// No field yet, or no room to grow it where it is (a slope, a tree): a new patch beside water, else near home.
		return byWater != null ? byWater : this.firstPlotNearHome(npc, house, homeX, homeY, homeZ);
	}

	/** Farmland this household tilled: its owner is the NPC or someone it shares with (husband or wife, parents, children). */
	private boolean ours(EntityNPC npc, int x, int y, int z) {
		UUID owner = BlockOwners.ownerAt(npc.world, x, y, z);
		return owner != null && Ownership.shares(npc.world, owner, npc.getUuid());
	}

	private boolean nextToField(EntityNPC npc, int x, int y, int z) {
		TilePos pos = new TilePos();
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] side : sides) {
			if (npc.world.getBlockType(pos.set(x + side[0], y, z + side[1])) == Blocks.FARMLAND_DIRT && this.ours(npc, x + side[0], y, z + side[1])) {
				return true;
			}
		}

		return false;
	}

	/** Grass or dirt with open air above, or only tall grass or a flower to knock off first: what a hoe can till. */
	private static boolean tillable(World world, int x, int y, int z) {
		TilePos pos = new TilePos(x, y, z);
		Block<?> block = world.getBlockType(pos);
		Block<?> above = world.getBlockType(pos.set(x, y + 1, z));
		return (block == Blocks.GRASS || block == Blocks.DIRT) && (above == Blocks.AIR || above.getMaterial() == Materials.PLANT);
	}

	/** Water within the game's reach for wetting farmland, at the same level. */
	private static boolean nearWater(World world, int x, int y, int z) {
		TilePos pos = new TilePos();
		for (int dx = -WATER_REACH; dx <= WATER_REACH; dx++) {
			for (int dz = -WATER_REACH; dz <= WATER_REACH; dz++) {
				if (world.getBlockType(pos.set(x + dx, y, z + dz)).getMaterial() == Materials.WATER) {
					return true;
				}
			}
		}

		return false;
	}

	/** Whether a column is in or right beside the house, which a field keeps clear of. */
	private static boolean nearHouse(@Nullable HomesteadRegister.Homestead house, int x, int z) {
		return house != null && x >= house.x() - HOUSE_CLEARANCE && x < house.x() + house.width() + HOUSE_CLEARANCE
			&& z >= house.z() - HOUSE_CLEARANCE && z < house.z() + house.depth() + HOUSE_CLEARANCE;
	}

	/** Somewhere to start a field with no water near: the tillable ground nearest home, outside the house's clearance. */
	@Nullable
	private Plot firstPlotNearHome(EntityNPC npc, @Nullable HomesteadRegister.Homestead house, int homeX, int homeY, int homeZ) {
		for (int ring = HOUSE_CLEARANCE; ring <= FIELD_RADIUS; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || nearHouse(house, homeX + dx, homeZ + dz)) {
						continue;
					}

					for (int y = homeY - FIELD_HEIGHT; y <= homeY + FIELD_HEIGHT; y++) {
						if (tillable(npc.world, homeX + dx, y, homeZ + dz)) {
							return new Plot(homeX + dx, y, homeZ + dz, Task.TILL);
						}
					}
				}
			}
		}

		return null;
	}

	@Override
	public void stop(EntityNPC npc) {
		this.breaking.stop();
		npc.navigator.stop();
	}

	@Override
	public String doing() {
		return "farming";
	}

	@Override
	public String describe() {
		return switch (this.step) {
			case FETCH_HOE -> "fetching a hoe";
			case FETCH_SEEDS -> "fetching seeds";
			case WORK -> this.plot == null ? "in the field" : switch (this.plot.task()) {
				case HARVEST -> "harvesting wheat";
				case SOW -> "sowing seeds";
				case TILL -> "tilling the field";
			};
			case STORE -> "bringing the harvest home";
		};
	}
}
