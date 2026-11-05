package lifeblood.event;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;

/**
 * A player gave an NPC something, and the NPC took it.
 *
 * @param npc who received it
 * @param giver who gave it
 * @param gift one of the item, as given
 */
public record GiftEvent(EntityNPC npc, Player giver, ItemStack gift) {
}
