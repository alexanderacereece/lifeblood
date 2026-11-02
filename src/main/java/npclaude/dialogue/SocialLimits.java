package npclaude.dialogue;

import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;
import npclaude.social.Memory;

/**
 * Pacing for making friends: one gift per NPC per in-game day, and five chats with an NPC per real-world minute.
 * Each one counts for more because of it. /npc limits off lifts both, for playtesting.
 */
public final class SocialLimits {
	private static final int CHATS_PER_MINUTE = 5;
	private static final long MINUTE_MILLIS = 60_000L;

	private static boolean enabled = true;

	private SocialLimits() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean enabled) {
		SocialLimits.enabled = enabled;
	}

	/** Whether this player has already given this NPC something today (since the last dawn). */
	public static boolean giftedToday(EntityNPC npc, Player player) {
		return enabled && npc.social.getLastGiftDay(player.uuid) == today(npc);
	}

	public static void recordGift(EntityNPC npc, Player player) {
		npc.social.setLastGiftDay(player.uuid, today(npc));
	}

	private static long today(EntityNPC npc) {
		return npc.world.getWorldTime() / Memory.TICKS_PER_DAY;
	}

	/** Takes up one of this minute's chats, or returns false if they've had their five. */
	public static boolean tryChat(EntityNPC npc, Player player) {
		return !enabled || npc.social.tryChat(player.uuid, CHATS_PER_MINUTE, MINUTE_MILLIS);
	}
}
