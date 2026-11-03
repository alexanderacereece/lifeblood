package npclaude.inventory;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.InventoryAction;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.IArmorItem;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.container.ContainerInventory;
import net.minecraft.core.player.inventory.menu.MenuAbstract;
import net.minecraft.core.player.inventory.slot.Slot;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.NotNull;

/**
 * The NPC's inventory on top, laid out like the player's own: armor column beside its preview, three rows of pack,
 * and a bottom row that starts with the hand slot, like a hotbar. The player's inventory is below.
 * NPC slot indexes match {@link NpcInventory} indexes.
 */
public class NpcMenu extends MenuAbstract {
	private static final int PLAYER_START = NpcInventory.SIZE;
	private static final int HOTBAR_START = PLAYER_START + 27;
	/** How many pack slots share the bottom row with the hand. */
	private static final int PACK_IN_HOTBAR = 8;
	/** Where the player's inventory starts on the screen. */
	public static final int PLAYER_TOP = 172;

	public final EntityNPC npc;

	public NpcMenu(ContainerInventory playerInventory, EntityNPC npc) {
		this.npc = npc;
		NpcInventory inventory = npc.inventory;
		for (int i = 0; i < NpcInventory.ARMOR_SIZE; i++) {
			this.addSlot(new NpcArmorSlot(inventory, NpcInventory.ARMOR_START + i, 8, 8 + i * 18, HumanArmorShape.values()[i]));
		}

		this.addSlot(new Slot(inventory, NpcInventory.HAND_SLOT, 8, 142));
		for (int i = 0; i < NpcInventory.PACK_SIZE; i++) {
			int row = (i - PACK_IN_HOTBAR) / 9;
			int x = i < PACK_IN_HOTBAR ? 26 + i * 18 : 8 + (i - PACK_IN_HOTBAR) % 9 * 18;
			int y = i < PACK_IN_HOTBAR ? 142 : 84 + row * 18;
			this.addSlot(new Slot(inventory, NpcInventory.PACK_START + i, x, y));
		}

		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 9; x++) {
				this.addSlot(new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, PLAYER_TOP + y * 18));
			}
		}

		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(playerInventory, i, 8 + i * 18, PLAYER_TOP + 58));
		}
	}

	@Override
	public boolean stillValid(@NotNull Player player) {
		return this.npc.inventory.stillValid(player);
	}

	/** Slots affected by "move all" style clicks: the clicked slot's own group. */
	@Override
	public IntList getMoveSlots(@NotNull InventoryAction action, @NotNull Slot slot, int target, Player player) {
		if (slot.index < NpcInventory.HAND_SLOT) {
			return this.getSlots(NpcInventory.ARMOR_START, NpcInventory.ARMOR_SIZE, false);
		}

		if (slot.index < PLAYER_START) {
			return this.getSlots(NpcInventory.HAND_SLOT, 1 + NpcInventory.PACK_SIZE, false);
		}

		if (action == InventoryAction.MOVE_ALL) {
			return slot.index < HOTBAR_START ? this.getSlots(PLAYER_START, 27, false) : this.getSlots(HOTBAR_START, 9, false);
		}

		return this.getSlots(PLAYER_START, 36, false);
	}

	/** Where shift-clicked items go: NPC items to the player; player items onto the NPC (armor to armor slots). */
	@Override
	public IntList getTargetSlots(@NotNull InventoryAction action, @NotNull Slot slot, int target, Player player) {
		if (slot.index < PLAYER_START) {
			return this.getSlots(PLAYER_START, 36, true);
		}

		IntList targets = new IntArrayList();
		ItemStack stack = slot.getItemStack();
		if (stack != null && stack.getItem() instanceof IArmorItem) {
			targets.addAll(this.getSlots(NpcInventory.ARMOR_START, NpcInventory.ARMOR_SIZE, false));
		} else {
			targets.add(NpcInventory.HAND_SLOT);
		}

		targets.addAll(this.getSlots(NpcInventory.PACK_START, NpcInventory.PACK_SIZE, false));
		return targets;
	}
}
