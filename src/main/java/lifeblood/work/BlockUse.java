package lifeblood.work;

import java.util.function.Predicate;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.tool.ItemToolHoe;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.inventory.NpcInventory;
import lifeblood.ownership.BlockOwners;

/**
 * Using an item on a block as a player right-clicks it: placing a sapling or a block, sowing seeds, tilling with a hoe.
 * The item's own code does the work (with no player, as a dispenser would), so its rules apply: seeds only take on
 * farmland, a hoe only tills grass and dirt with air above, a sapling only stays on soil. Only within reach, with the
 * item in hand, swinging the arm; anything placed is the NPC's.
 */
public final class BlockUse {
	private BlockUse() {
	}

	/**
	 * Takes an item matching into the hand from the pack, if it isn't held already. Returns whether it's in hand now.
	 */
	public static boolean holdItem(EntityNPC npc, Predicate<ItemStack> matches) {
		ItemStack held = npc.getHeldItem();
		if (held != null && matches.test(held)) {
			return true;
		}

		int slot = npc.inventory.findSlot(matches);
		if (slot < 0) {
			return false;
		}

		npc.inventory.swapWithHand(slot);
		return true;
	}

	/** Uses the held item on the top face of a block (the one under where it goes). Returns whether it did anything. */
	public static boolean useOnTop(EntityNPC npc, int x, int y, int z) {
		ItemStack held = npc.getHeldItem();
		if (held == null || !Reach.canReach(npc, x, y, z)) {
			return false;
		}

		Reach.lookAt(npc, x, y, z);
		npc.swingArm();
		TilePos pos = new TilePos(x, y, z);
		boolean used = BlockOwners.placingAs(npc.getUuid(), () -> held.getItem().onUseOnBlock(held, npc.world, null, pos, Side.TOP, 0.5, 0.5));
		tidyHand(npc);
		return used;
	}

	/** Tills grass or dirt into farmland with the held hoe, as a player does. Returns whether it tilled. */
	public static boolean till(EntityNPC npc, int x, int y, int z) {
		ItemStack held = npc.getHeldItem();
		if (held == null || !(held.getItem() instanceof ItemToolHoe hoe) || !Reach.canReach(npc, x, y, z)) {
			return false;
		}

		Reach.lookAt(npc, x, y, z);
		npc.swingArm();
		boolean tilled = BlockOwners.placingAs(npc.getUuid(), () -> hoe.till(held, null, npc.world, new TilePos(x, y, z), Side.TOP));
		tidyHand(npc);
		return tilled;
	}

	/** Clears a used-up stack from the hand. */
	private static void tidyHand(EntityNPC npc) {
		ItemStack held = npc.getHeldItem();
		if (held != null && held.stackSize <= 0) {
			npc.inventory.setItem(NpcInventory.HAND_SLOT, null);
		}
	}
}
