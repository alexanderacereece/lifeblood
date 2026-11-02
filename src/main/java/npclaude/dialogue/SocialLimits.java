package npclaude.dialogue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
	/** When each player last chatted with each NPC, by NPC then player. Real time, so not saved. */
	private static final Map<UUID, Map<UUID, Deque<Long>>> CHATS = new HashMap<>();

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

	/** Takes up one of this minute's chats, or returns false if they've had their three. */
	public static boolean tryChat(EntityNPC npc, Player player) {
		if (!enabled) {
			return true;
		}

		long now = System.currentTimeMillis();
		Deque<Long> times = CHATS.computeIfAbsent(npc.getUuid(), id -> new HashMap<>()).computeIfAbsent(player.uuid, id -> new ArrayDeque<>());
		while (!times.isEmpty() && now - times.peekFirst() >= MINUTE_MILLIS) {
			times.pollFirst();
		}

		if (times.size() >= CHATS_PER_MINUTE) {
			return false;
		}

		times.addLast(now);
		return true;
	}
}
