package lifeblood.social;

import com.mojang.nbt.tags.CompoundTag;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import lifeblood.family.Romance;
import org.jetbrains.annotations.Nullable;

/**
 * The NPC's dealings with particular people: who saved its life, the daily gift each player has given, and
 * anything it's waiting to ask a player (out on a date, or to marry). Its love life is tended from here too.
 */
public final class NpcSocial implements NpcPart {
	/** How long an NPC must survive after being rescued for it to count (10 seconds). */
	private static final int RESCUE_CONFIRM_TICKS = 200;
	/** How often the NPC tends its love life: a relationship to keep up, or someone to ask out (1 minute). */

	private final EntityNPC npc;
	/** Someone who just killed what was about to kill this NPC; becomes "saved my life" if it survives a while. */
	@Nullable
	private Entity rescuer;
	private int rescueTick;
	/** Something it wants to ask a player next time they talk, and which player. */
	@Nullable
	private Romance.Request romanceRequest;
	@Nullable
	private UUID romanceRequestPlayer;
	/** The in-game day each player last gave it a gift, for the one-gift-a-day limit. */
	private final Map<UUID, Long> lastGiftDays = new HashMap<>();
	/** When each player chatted with it lately (real time), for the chats-a-minute limit. Not saved. */
	private final Map<UUID, Deque<Long>> chatTimes = new HashMap<>();

	public NpcSocial(EntityNPC npc) {
		this.npc = npc;
	}

	@Override
	public void tick() {
		if (this.rescuer != null && this.npc.tickCount - this.rescueTick >= RESCUE_CONFIRM_TICKS) {
			Entity saviour = this.rescuer;
			this.rescuer = null;
			if (this.npc.isAlive() && !saviour.removed) {
				this.npc.remember(MemoryType.SAVED_MY_LIFE, saviour, 1.0F);
				this.npc.voice.bark("thanks_saved", saviour, null, true);
			}
		}

		if ((this.npc.tickCount + this.npc.id) % Romance.CHECK_INTERVAL == 0) {
			Romance.tick(this.npc);
		}
	}

	/** Someone killed whatever was attacking this badly hurt NPC; if it lives another ten seconds, they saved its life. */
	public void noticeRescue(Entity rescuer) {
		this.rescuer = rescuer;
		this.rescueTick = this.npc.tickCount;
	}

	public void forgetRescue() {
		this.rescuer = null;
	}

	@Nullable
	public Romance.Request getRomanceRequest() {
		return this.romanceRequest;
	}

	/** What it wants to ask this player, if anything. */
	@Nullable
	public Romance.Request getRomanceRequest(Player player) {
		return this.romanceRequest != null && player.uuid.equals(this.romanceRequestPlayer) ? this.romanceRequest : null;
	}

	public void setRomanceRequest(@Nullable Romance.Request request, @Nullable Player player) {
		this.romanceRequest = request;
		this.romanceRequestPlayer = player != null ? player.uuid : null;
	}

	/** The in-game day this player last gave it a gift; -1 if never. */
	public long getLastGiftDay(UUID player) {
		return this.lastGiftDays.getOrDefault(player, -1L);
	}

	public void setLastGiftDay(UUID player, long day) {
		this.lastGiftDays.put(player, day);
	}

	/** Takes up one of this player's chats for the window, or returns false if they've had them all. */
	public boolean tryChat(UUID player, int allowed, long windowMillis) {
		long now = System.currentTimeMillis();
		Deque<Long> times = this.chatTimes.computeIfAbsent(player, id -> new ArrayDeque<>());
		while (!times.isEmpty() && now - times.peekFirst() >= windowMillis) {
			times.pollFirst();
		}

		if (times.size() >= allowed) {
			return false;
		}

		times.addLast(now);
		return true;
	}

	/** Gift days are saved; a pending question and a rescue in progress aren't. */
	@Override
	public void save(CompoundTag tag) {
		CompoundTag giftTag = new CompoundTag();
		for (Map.Entry<UUID, Long> entry : this.lastGiftDays.entrySet()) {
			giftTag.putLong(entry.getKey().toString(), entry.getValue());
		}

		tag.putCompound("GiftDays", giftTag);
	}

	@Override
	public void load(CompoundTag tag) {
		this.lastGiftDays.clear();
		if (!tag.containsKey("GiftDays")) {
			return;
		}

		CompoundTag giftTag = tag.getCompound("GiftDays");
		for (String key : giftTag.getValue().keySet()) {
			try {
				this.lastGiftDays.put(UUID.fromString(key), giftTag.getLong(key));
			} catch (IllegalArgumentException ignored) {
				// A damaged entry is dropped rather than failing the whole load.
			}
		}
	}
}
