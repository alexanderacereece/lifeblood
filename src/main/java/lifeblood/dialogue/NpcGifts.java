package lifeblood.dialogue;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;
import lifeblood.item.NpcItems;
import lifeblood.personality.ItemDefaults;
import lifeblood.personality.Quirk;
import lifeblood.personality.TasteCategory;
import lifeblood.personality.Tastes;
import lifeblood.social.MemoryType;
import lifeblood.speech.Word;

/**
 * Giving an NPC whatever the player is holding: one of it goes into the NPC's inventory and is theirs from then on,
 * to eat, wear or use. They remember the first gift of the day, fondly or not depending on how they feel about the
 * item (handing someone dirt is an insult); later ones that day are kept, but don't move them. Since only people who
 * share what an NPC owns can open its inventory, a gift can't be taken back and given again.
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
		boolean food = Tastes.isFood(held.getItem());
		Word word = food ? Word.ofFood(held.getItem()) : Word.ofItem(gift);
		boolean amulet = NpcItems.isGuardianAmulet(gift);
		if (amulet && npc.inventory.hasAmulet()) {
			String line = npc.voice.line("gift_amulet_already", player, null);
			return line != null ? line : "I already have one. But thank you.";
		}

		if (!npc.inventory.addToPack(gift)) {
			String line = npc.voice.line("gift_full", player, context -> context.word("item", word));
			return line != null ? line : "My hands are full.";
		}

		npc.inventory.wearBetterArmor();
		boolean first = amulet || !SocialLimits.giftedToday(npc, player);
		if (!amulet) {
			SocialLimits.recordGift(npc, player);
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

		if (amulet) {
			npc.remember(MemoryType.GAVE_ME_AN_AMULET, player, 1.0F);
			String line = npc.voice.line("gift_amulet", player, null);
			return line != null ? line : "For me? I... thank you.";
		}

		float like = npc.personality.tastes.like(held.getItem());
		if (!first) {
			String line = npc.voice.line("gift_again", player, context -> context.word("item", word).fact("taste", Tastes.describe(like)));
			return line != null ? line : "Thanks. I'll put it to use.";
		}

		// A gift on their birthday means more.
		float birthday = npc.life.isBirthday() ? 2.0F : 1.0F;
		ItemDefaults.Default defaults = ItemDefaults.of(held.getItem());
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

		String line = npc.voice.line("gift", player, context -> context.word("item", word).fact("kind", defaults.main().id())
			.fact("taste", Tastes.describe(like)).fact("rude_gift", rude ? "yes" : "no")
			.fact("surprising", Math.abs(like - defaults.base()) >= 0.6F ? "yes" : "no"));
		return line != null ? line : "Thanks.";
	}
}
