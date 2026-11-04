package lifeblood.inventory;

import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.IArmorItem;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.player.inventory.slot.Slot;
import net.minecraft.core.player.inventory.slot.SlotArmor;

/** Same rules as the player's armor slots (vanilla SlotArmor is tied to the player's own menu). */
public class NpcArmorSlot extends Slot {
	private final HumanArmorShape shape;

	public NpcArmorSlot(Container container, int index, int x, int y, HumanArmorShape shape) {
		super(container, index, x, y);
		this.shape = shape;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	/** Armor must match the slot; like for players, anything else (e.g. a pumpkin) may be worn on the head. */
	@Override
	public boolean mayPlace(ItemStack stack) {
		if (stack.getItem() instanceof IArmorItem<?> armor) {
			return armor.getArmorShape() == this.shape;
		}

		return this.shape == HumanArmorShape.HEAD;
	}

	@Override
	public String getItemIcon() {
		return SlotArmor.armorOutlines[this.shape.getSlotIndex()];
	}
}
