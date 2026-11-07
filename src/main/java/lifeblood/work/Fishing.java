package lifeblood.work;

import java.util.Random;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.item.ItemFishingRod;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.sound.SoundCategory;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.inventory.NpcInventory;

/**
 * Fishing by the player's rules (EntityFishingBobber), which only work for a player's own float: each tick the line
 * is in the water, a bite comes one time in 500 (300 in the rain, 100 fewer under algae) and lasts 10 to 39 ticks;
 * reeling in during a bite lands a raw fish and wears the rod by one. The NPC casts at a water block within reach,
 * reels in after a person's reaction time, and casts again. The float isn't drawn yet.
 */
public final class Fishing {
	private static final int CATCH_RATE = 500;
	private static final int RAIN_BONUS = 200;
	private static final int ALGAE_BONUS = 100;
	private static final int BITE_MIN = 10;
	private static final int BITE_SPREAD = 30;
	/** How long it takes them to notice a bite and reel in, in ticks: a quarter to three quarters of a second. */
	private static final int REACT_MIN = 5;
	private static final int REACT_SPREAD = 10;
	/** The most ticks one call counts, as for digging, so a long pause doesn't land a fish at once. */
	private static final int MAX_ELAPSED = 20;

	private final EntityNPC npc;
	private boolean cast;
	private int waterX;
	private int waterY;
	private int waterZ;
	/** Ticks left in the bite under way (0 for none), and how long until they react to it. */
	private int bite;
	private int reaction;
	private long lastTick;

	public Fishing(EntityNPC npc) {
		this.npc = npc;
	}

	/** Whether this is open water a float can sit on: water with air above. */
	public static boolean isFishable(World world, int x, int y, int z) {
		TilePos pos = new TilePos(x, y, z);
		return world.getBlockType(pos).getMaterial() == Materials.WATER
			&& world.getBlockType(pos.set(x, y + 1, z)) == Blocks.AIR;
	}

	/** Whether they're holding a fishing rod. */
	public static boolean isRod(ItemStack stack) {
		return stack.getItem() instanceof ItemFishingRod;
	}

	/**
	 * Fishes for one tick at this water block (which must be within reach, with a rod in hand). Returns whether a fish
	 * was landed this tick; it's flung toward them, to be picked up.
	 */
	public boolean fish(int x, int y, int z) {
		World world = this.npc.world;
		ItemStack rod = this.npc.getHeldItem();
		if (rod == null || !isRod(rod) || !Reach.canReach(this.npc, x, y, z) || !isFishable(world, x, y, z)) {
			this.cast = false;
			return false;
		}

		Reach.lookAt(this.npc, x, y, z);
		if (!this.cast || x != this.waterX || y != this.waterY || z != this.waterZ) {
			this.castAt(x, y, z);
			return false;
		}

		long elapsed = Math.max(1, Math.min(MAX_ELAPSED, world.getWorldTime() - this.lastTick));
		this.lastTick = world.getWorldTime();
		Random random = this.npc.getRandom();
		for (int tick = 0; tick < elapsed; tick++) {
			if (this.bite > 0) {
				this.bite--;
				if (--this.reaction <= 0) {
					this.reelIn(rod, true);
					return true;
				}
			} else if (random.nextInt(this.catchRate()) == 0) {
				this.bite = BITE_MIN + random.nextInt(BITE_SPREAD);
				this.reaction = REACT_MIN + random.nextInt(REACT_SPREAD);
				world.playSoundEffect(null, SoundCategory.WORLD_SOUNDS, x + 0.5, y + 1.0, z + 0.5, "random.splash", 0.25F,
					1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
			}
		}

		return false;
	}

	/** Stops fishing, reeling the line in empty. */
	public void stop() {
		if (this.cast) {
			ItemStack rod = this.npc.getHeldItem();
			if (rod != null && isRod(rod)) {
				this.reelIn(rod, false);
			}
		}
	}

	/** A bite comes one time in 500 each tick, more often in the rain or under algae. */
	private int catchRate() {
		TilePos above = new TilePos(this.waterX, this.waterY + 1, this.waterZ);
		int rate = CATCH_RATE;
		if (this.npc.world.isBlockBeingRainedOn(above)) {
			rate -= RAIN_BONUS;
		}

		if (this.npc.world.getBlockType(above) == Blocks.ALGAE) {
			rate -= ALGAE_BONUS;
		}

		return rate;
	}

	private void castAt(int x, int y, int z) {
		this.cast = true;
		this.waterX = x;
		this.waterY = y;
		this.waterZ = z;
		this.bite = 0;
		this.lastTick = this.npc.world.getWorldTime();
		this.npc.swingArm();
		this.npc.world.playSoundAtEntity(null, this.npc, "random.bow", 0.5F, 0.4F / (this.npc.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	/** Reels in: a fish if there was a bite, flung toward them as the player's float does; the rod wears by one for a catch. */
	private void reelIn(ItemStack rod, boolean caught) {
		this.cast = false;
		this.npc.swingArm();
		if (caught) {
			EntityItem fish = new EntityItem(this.npc.world, this.waterX + 0.5, this.waterY + 1.0, this.waterZ + 0.5, new ItemStack(Items.FOOD_FISH_RAW));
			double dx = this.npc.x - fish.x;
			double dy = this.npc.y - fish.y;
			double dz = this.npc.z - fish.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			fish.xd = dx * 0.1;
			fish.yd = dy * 0.1 + Math.sqrt(distance) * 0.08;
			fish.zd = dz * 0.1;
			this.npc.world.entityJoinedWorld(fish);
			rod.damageItem(1, this.npc);
			if (rod.stackSize <= 0) {
				this.npc.inventory.setItem(NpcInventory.HAND_SLOT, null);
			}
		}
	}
}
