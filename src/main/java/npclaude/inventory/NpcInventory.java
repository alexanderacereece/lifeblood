package npclaude.inventory;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.Arrays;
import java.util.function.Predicate;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.InventorySorter;
import net.minecraft.core.player.inventory.container.Container;
import npclaude.entity.EntityNPC;
import npclaude.item.NpcItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an NPC carries: 4 armor slots (head, chest, legs, boots, matching HumanArmorShape order),
 * the item in its hand, and a 3x3 pack.
 */
public class NpcInventory implements Container {
	public static final int ARMOR_START = 0;
	public static final int ARMOR_SIZE = 4;
	public static final int HAND_SLOT = 4;
	public static final int PACK_START = 5;
	public static final int PACK_SIZE = 9;
	public static final int SIZE = PACK_START + PACK_SIZE;
	/** Players further than this (8 blocks) can't keep the inventory open. */
	private static final double MAX_USE_DISTANCE_SQR = 64.0;

	private final EntityNPC npc;
	private final ItemStack[] items = new ItemStack[SIZE];

	public NpcInventory(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Nullable
	@Override
	public ItemStack getItem(int slot) {
		return this.items[slot];
	}

	@Nullable
	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack stack = this.items[slot];
		if (stack == null) {
			return null;
		}

		if (stack.stackSize <= amount) {
			this.items[slot] = null;
			return stack;
		}

		ItemStack taken = stack.splitStack(amount);
		if (stack.stackSize <= 0) {
			this.items[slot] = null;
		}

		return taken;
	}

	@Override
	public void setItem(int slot, @Nullable ItemStack stack) {
		this.items[slot] = stack;
		if (stack != null && stack.stackSize > this.getMaxStackSize()) {
			stack.stackSize = this.getMaxStackSize();
		}
	}

	@NotNull
	@Override
	public String getNameTranslationKey() {
		return this.npc.nickname;
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return this.npc.isAlive() && !this.npc.removed && this.npc.distanceToSqr(player) <= MAX_USE_DISTANCE_SQR;
	}

	/** A guardian amulet, once given, is the NPC's to keep: it can't be taken back out. */
	@Override
	public boolean locked(int slot) {
		return NpcItems.isGuardianAmulet(this.items[slot]);
	}

	/** Sorts only the pack, so armor and the held item stay put. */
	@Override
	public void sort() {
		ItemStack[] pack = new ItemStack[PACK_SIZE];
		System.arraycopy(this.items, PACK_START, pack, 0, PACK_SIZE);
		InventorySorter.sortInventory(pack);
		System.arraycopy(pack, 0, this.items, PACK_START, PACK_SIZE);
	}

	/** The first hand or pack slot whose item matches, checking the hand first; -1 if none. */
	public int findSlot(Predicate<ItemStack> matches) {
		for (int i = -1; i < PACK_SIZE; i++) {
			int slot = i < 0 ? HAND_SLOT : PACK_START + i;
			ItemStack stack = this.items[slot];
			if (stack != null && matches.test(stack)) {
				return slot;
			}
		}

		return -1;
	}

	/**
	 * Puts a stack into the pack: onto a matching stack with room for all of it, else into the first empty slot.
	 * Returns false (and changes nothing) if it doesn't fit.
	 */
	public boolean addToPack(ItemStack stack) {
		for (int i = 0; i < PACK_SIZE; i++) {
			ItemStack existing = this.items[PACK_START + i];
			if (existing != null && existing.canStackWith(stack)
				&& existing.stackSize + stack.stackSize <= Math.min(existing.getMaxStackSize(), this.getMaxStackSize())) {
				existing.stackSize += stack.stackSize;
				return true;
			}
		}

		for (int i = 0; i < PACK_SIZE; i++) {
			if (this.items[PACK_START + i] == null) {
				this.items[PACK_START + i] = stack;
				return true;
			}
		}

		return false;
	}

	/** Swaps an item into the hand, putting what was held in its place, like switching hotbar slots. */
	public void swapWithHand(int slot) {
		if (slot != HAND_SLOT) {
			ItemStack held = this.items[HAND_SLOT];
			this.items[HAND_SLOT] = this.items[slot];
			this.items[slot] = held;
		}
	}

	/** Uses up one item from the hand or pack that matches; returns whether there was one. */
	public boolean consumeOne(Predicate<ItemStack> matches) {
		int slot = this.findSlot(matches);
		if (slot < 0) {
			return false;
		}

		ItemStack stack = this.items[slot];
		stack.stackSize--;
		if (stack.stackSize <= 0) {
			this.items[slot] = null;
		}

		return true;
	}

	public void dropAll() {
		for (int i = 0; i < SIZE; i++) {
			if (this.items[i] != null) {
				this.npc.dropItem(this.items[i], 0.0F);
				this.items[i] = null;
			}
		}
	}

	public ListTag save() {
		ListTag list = new ListTag();
		for (int i = 0; i < SIZE; i++) {
			if (this.items[i] != null) {
				CompoundTag tag = new CompoundTag();
				tag.putByte("Slot", (byte) i);
				this.items[i].writeToNBT(tag);
				list.addTag(tag);
			}
		}

		return list;
	}

	public void load(ListTag list) {
		Arrays.fill(this.items, null);
		for (Tag<?> entry : list) {
			if (entry instanceof CompoundTag tag) {
				int slot = tag.getByte("Slot") & 0xFF;
				if (slot < SIZE) {
					this.items[slot] = ItemStack.readItemStackFromNbt(tag);
				}
			}
		}
	}
}
