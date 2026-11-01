package npclaude.dialogue;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import npclaude.entity.EntityNPC;
import npclaude.personality.ItemDefaults;
import npclaude.personality.Quirk;
import npclaude.personality.TasteCategory;
import npclaude.personality.Tastes;
import npclaude.social.MemoryType;
import npclaude.speech.Word;

/**
 * Giving an NPC whatever the player is holding: it goes into the NPC's inventory, and the NPC remembers it,
 * fondly or not depending on how it feels about the item. Handing someone dirt is an insult.
 */
public final class NpcGifts {
	/** Below this, a gift of junk or something gross is taken as an insult rather than a dud. */
	private static final float INSULT_LIKE = -0.55F;

	private NpcGifts() {
	}

	/** Hands over the held stack and returns what the NPC says about it. */
	public static String give(EntityNPC npc, Player player) {
		ItemStack held = player.inventory.getCurrentItem();
		if (held == null) {
			return "...";
		}

		ItemStack gift = held.copy();
		gift.stackSize = 1;
		if (!npc.inventory.addToPack(gift)) {
			String line = npc.voice.line("gift_full", player, null);
			return line != null ? line : "I've got no room for that.";
		}

		// Only one of the stack changes hands.
		held.stackSize--;
		if (held.stackSize <= 0) {
			for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
				if (player.inventory.mainInventory[slot] == held) {
					player.inventory.mainInventory[slot] = null;
				}
			}
		}

		ItemDefaults.Default defaults = ItemDefaults.of(held.getItem());
		boolean food = Tastes.isFood(held.getItem());
		float like = npc.personality.tastes.like(held.getItem());
		boolean rude = (defaults.is(TasteCategory.JUNK) || defaults.is(TasteCategory.GROSS)) && like <= INSULT_LIKE;
		if (food) {
			float taste = Math.min(1.0F, Math.abs(like) * (npc.personality.has(Quirk.GLUTTON) ? 2.0F : 1.0F));
			if (like >= 0.2F) {
				npc.remember(MemoryType.GAVE_ME_FOOD_I_LIKE, player, taste);
			} else if (like <= -0.2F) {
				npc.remember(MemoryType.GAVE_ME_FOOD_I_HATE, player, taste);
			} else {
				npc.remember(MemoryType.GAVE_ME_A_GIFT, player, 0.5F);
			}
		} else if (rude) {
			npc.remember(MemoryType.INSULTED_ME, player, -like * 0.6F);
		} else if (like <= -0.2F) {
			npc.remember(MemoryType.GAVE_ME_JUNK, player, -like);
		} else {
			npc.remember(MemoryType.GAVE_ME_A_GIFT, player, 0.4F + Math.max(0.0F, like));
		}

		Word word = food ? Word.ofFood(held.getItem()) : Word.ofItem(gift);
		String line = npc.voice.line("gift", player, context -> context.word("item", word).fact("kind", defaults.main().id())
			.fact("taste", EntityNPC.tasteOf(like)).fact("rude_gift", rude ? "yes" : "no")
			.fact("surprising", Math.abs(like - defaults.base()) >= 0.6F ? "yes" : "no"));
		return line != null ? line : "Thanks.";
	}
}
