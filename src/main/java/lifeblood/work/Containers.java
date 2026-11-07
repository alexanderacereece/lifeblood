package lifeblood.work;

import java.util.function.Predicate;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.inventory.NpcInventory;
import org.jetbrains.annotations.Nullable;

/**
 * Taking from and putting into chests and other containers, within reach, the way a player moves stacks in a chest's
 * screen: whole stacks, merging onto stacks of the same thing first. Whose container it is is for the caller to
 * decide (see ownership).
 */
public final class Containers {
	private Containers() {
	}

	/** The container at a block within reach, or null if there isn't one or it's too far. */
	@Nullable
	public static Container at(EntityNPC npc, int x, int y, int z) {
		if (!Reach.canReach(npc, x, y, z)) {
			return null;
		}

		TileEntity tileEntity = npc.world.getTileEntity(new TilePos(x, y, z));
		return tileEntity instanceof Container container ? container : null;
	}

	/** Moves stacks matching from the hand and pack (not armor) into the container; returns how many items went in. */
	public static int store(EntityNPC npc, Container container, Predicate<ItemStack> matches) {
		int moved = 0;
		for (int i = -1; i < NpcInventory.PACK_SIZE; i++) {
			int slot = i < 0 ? NpcInventory.HAND_SLOT : NpcInventory.PACK_START + i;
			ItemStack stack = npc.inventory.getItem(slot);
			if (stack == null || !matches.test(stack)) {
				continue;
			}

			int before = stack.stackSize;
			insert(container, stack);
			moved += before - stack.stackSize;
			npc.inventory.setItem(slot, stack.stackSize > 0 ? stack : null);
		}

		if (moved > 0) {
			container.setChanged();
		}

		return moved;
	}

	/** Takes up to this many items matching from the container into the pack; returns how many it took. */
	public static int take(EntityNPC npc, Container container, Predicate<ItemStack> matches, int most) {
		int taken = 0;
		for (int slot = 0; slot < container.getContainerSize() && taken < most; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack == null || !matches.test(stack)) {
				continue;
			}

			int amount = Math.min(stack.stackSize, most - taken);
			ItemStack part = stack.copy();
			part.stackSize = amount;
			if (!npc.inventory.addToPack(part)) {
				break;
			}

			stack.stackSize -= amount;
			container.setItem(slot, stack.stackSize > 0 ? stack : null);
			taken += amount;
		}

		if (taken > 0) {
			container.setChanged();
		}

		return taken;
	}

	/** How many items matching the container holds. */
	public static int count(Container container, Predicate<ItemStack> matches) {
		int count = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack != null && matches.test(stack)) {
				count += stack.stackSize;
			}
		}

		return count;
	}

	/** Puts a stack into the container: onto matching stacks first, then empty slots. What doesn't fit stays in it. */
	public static void insert(Container container, ItemStack stack) {
		int limit = Math.min(stack.getMaxStackSize(), container.getMaxStackSize());
		for (int slot = 0; slot < container.getContainerSize() && stack.stackSize > 0; slot++) {
			ItemStack there = container.getItem(slot);
			if (there != null && there.canStackWith(stack) && there.stackSize < limit) {
				int moved = Math.min(stack.stackSize, limit - there.stackSize);
				there.stackSize += moved;
				stack.stackSize -= moved;
			}
		}

		for (int slot = 0; slot < container.getContainerSize() && stack.stackSize > 0; slot++) {
			if (container.getItem(slot) == null) {
				ItemStack part = stack.copy();
				part.stackSize = Math.min(stack.stackSize, limit);
				container.setItem(slot, part);
				stack.stackSize -= part.stackSize;
			}
		}
	}
}
