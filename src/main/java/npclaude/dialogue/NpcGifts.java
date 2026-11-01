package npclaude.dialogue;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import npclaude.entity.EntityNPC;
import npclaude.item.NpcItems;
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
		boolean amulet = NpcItems.isGuardianAmulet(gift);
		if (amulet && !npc.inventory.giveAmulet(gift)) {
			String line = npc.voice.line("gift_amulet_already", player, null);
			return line != null ? line : "I already have one. But thank you.";
		}

		if (!amulet) {
			SocialLimits.recordGift(npc, player);
		}

		// Only one of the stack changes hands, and it's theirs now: gifts don't go into their inventory, so they
		// can't be taken back out of it. (The amulet goes into its own locked slot.)
		held.stackSize--;
		if (held.stackSize <= 0) {
			for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
				if (player.inventory.mainInventory[slot] == held) {
					player.inventory.mainInventory[slot] = null;
				}
			}
		}

		if (amulet) {
			npc.remember(MemoryType.GAVE_ME_AN_AMULET, player, 1.0F);
			String line = npc.voice.line("gift_amulet", player, null);
			return line != null ? line : "For me? I... thank you.";
		}

		// A gift on their birthday means more.
		float birthday = npc.isBirthday() ? 2.0F : 1.0F;
		ItemDefaults.Default defaults = ItemDefaults.of(held.getItem());
		boolean food = Tastes.isFood(held.getItem());
		float like = npc.personality.tastes.like(held.getItem());
		boolean rude = (defaults.is(TasteCategory.JUNK) || defaults.is(TasteCategory.GROSS)) && like <= INSULT_LIKE;
		if (food) {
			float taste = Math.min(1.0F, Math.abs(like) * (npc.personality.has(Quirk.GLUTTON) ? 2.0F : 1.0F));
			if (like >= 0.2F) {
				npc.remember(MemoryType.GAVE_ME_FOOD_I_LIKE, player, taste * birthday);
			} else if (like <= -0.2F) {
				npc.remember(MemoryType.GAVE_ME_FOOD_I_HATE, player, taste);
			} else {
				npc.remember(MemoryType.GAVE_ME_A_GIFT, player, 0.5F * birthday);
			}
		} else if (rude) {
			npc.remember(MemoryType.INSULTED_ME, player, -like * 0.6F);
		} else if (like <= -0.2F) {
			npc.remember(MemoryType.GAVE_ME_JUNK, player, -like);
		} else {
			npc.remember(MemoryType.GAVE_ME_A_GIFT, player, (0.4F + Math.max(0.0F, like)) * birthday);
		}

		// Their reaction to their very favorite (or most hated) gives it away.
		Tastes tastes = npc.personality.tastes;
		if (food && gift.getItem() == tastes.favoriteFood()) {
			npc.voice.reveal("favorite_food");
		} else if (food && gift.getItem() == tastes.hatedFood()) {
			npc.voice.reveal("hated_food");
		} else if (gift.getItem() == tastes.favoriteThing()) {
			npc.voice.reveal("favorite_thing");
		} else if (gift.getItem() == tastes.hatedThing()) {
			npc.voice.reveal("hated_thing");
		}

		Word word = food ? Word.ofFood(held.getItem()) : Word.ofItem(gift);
		String line = npc.voice.line("gift", player, context -> context.word("item", word).fact("kind", defaults.main().id())
			.fact("taste", EntityNPC.tasteOf(like)).fact("rude_gift", rude ? "yes" : "no")
			.fact("surprising", Math.abs(like - defaults.base()) >= 0.6F ? "yes" : "no"));
		return line != null ? line : "Thanks.";
	}
}
