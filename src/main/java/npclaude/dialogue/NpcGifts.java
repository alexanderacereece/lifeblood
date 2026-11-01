package npclaude.dialogue;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import npclaude.entity.EntityNPC;
import npclaude.personality.FoodPreferences;
import npclaude.personality.Quirk;
import npclaude.social.MemoryType;
import npclaude.speech.Word;

/** Giving an NPC whatever the player is holding: it goes into the NPC's inventory, and the NPC remembers it. */
public final class NpcGifts {
	private NpcGifts() {
	}

	/** Hands over the held stack and returns what the NPC says about it. */
	public static String give(EntityNPC npc, Player player) {
		ItemStack held = player.inventory.getCurrentItem();
		if (held == null) {
			return "...";
		}

		if (!npc.inventory.addToPack(held)) {
			String line = npc.voice.line("gift_full", player, null);
			return line != null ? line : "I've got no room for that.";
		}

		for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
			if (player.inventory.mainInventory[slot] == held) {
				player.inventory.mainInventory[slot] = null;
			}
		}

		boolean food = FoodPreferences.isFood(held.getItem());
		float like = food ? npc.personality.food.like(held.getItem()) : 0.0F;
		float taste = Math.min(1.0F, Math.abs(like) * (npc.personality.has(Quirk.GLUTTON) ? 2.0F : 1.0F));
		if (food && like >= 0.2F) {
			npc.remember(MemoryType.GAVE_ME_FOOD_I_LIKE, player, taste);
		} else if (food && like <= -0.2F) {
			npc.remember(MemoryType.GAVE_ME_FOOD_I_HATE, player, taste);
		} else {
			npc.remember(MemoryType.GAVE_ME_A_GIFT, player, food ? 0.5F : 1.0F);
		}

		Word word = food ? Word.ofFood(held.getItem()) : Word.ofItem(held);
		String line = npc.voice.line("gift", player, context -> context.word("item", word).fact("kind", food ? "food" : "item")
			.fact("taste", EntityNPC.tasteOf(like)));
		return line != null ? line : "Thanks.";
	}
}
