package npclaude.speech;

import com.mojang.nbt.tags.CompoundTag;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.entity.EntityNPC;
import npclaude.personality.Quirk;
import npclaude.personality.Surroundings;
import npclaude.personality.Tastes;
import npclaude.personality.TraitAxis;
import npclaude.social.MemoryType;
import npclaude.social.Opinions;
import npclaude.words.WordBook;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * An NPC's voice: its speech style, what it said recently, and when it speaks up on its own, whether greeting
 * players, remarking on things, or chatting with other NPCs. Lines said aloud appear in the chat of players within
 * earshot, as "&lt;Name&gt; line".
 */
public class NpcVoice {
	public static final double HEARING_RANGE = 24.0;
	/** Chance a friendly NPC chat passes on a taught word. */
	private static final float WORD_SHARE_CHANCE = 0.3F;
	private static final int CHECK_INTERVAL = 20;
	private static final double GREET_RANGE = 6.0;
	private static final double IDLE_RANGE = 12.0;
	private static final double CHAT_PARTNER_RANGE = 5.0;
	/** Don't greet the same person again for five minutes. */
	private static final long GREET_COOLDOWN = 6000L;
	/** Between remarks made unprompted: two to four minutes, longer for the taciturn. */
	private static final long IDLE_COOLDOWN_MIN = 2400L;
	private static final long IDLE_COOLDOWN_SPREAD = 2400L;
	private static final long CHAT_COOLDOWN = 3600L;
	/** Between barks (reactions in a fight and the like), so an NPC doesn't narrate every hit. */
	private static final long BARK_COOLDOWN = 160L;
	/** Between any two lines from any NPCs, so a crowd doesn't flood the chat. */
	private static final long GLOBAL_GAP = 20L;
	private static final long REPLY_DELAY = 40L;
	/** Events where a catchphrase can slip in. */
	private static final Set<String> CASUAL = Set.of("greet", "idle", "smalltalk", "chat_open", "chat_reply", "dialogue_open");

	private static long lastAnyLine = Long.MIN_VALUE / 2;

	public final SpeechStyle style = new SpeechStyle();
	private final EntityNPC npc;
	/** When each line was last said, so they don't repeat. */
	private final Map<String, Long> usedLines = new HashMap<>();
	private final Map<UUID, Long> lastGreeted = new HashMap<>();
	private long nextBark;
	private long nextIdle;
	private long nextChat;
	@Nullable
	private EntityNPC replyTo;
	private long replyAt;

	/** Loves and hates the player has heard about; see {@link #isRevealed}. */
	private final Set<String> revealed = new HashSet<>();

	public NpcVoice(EntityNPC npc) {
		this.npc = npc;
	}

	/**
	 * A line for this event, without saying it aloud (e.g. for the dialogue box). Null if nothing fits.
	 * The extras callback adds facts and words specific to the event.
	 */
	@Nullable
	public String line(String event, @Nullable Entity listener, @Nullable Consumer<SpeechContext> extras) {
		if (!this.style.isRolled()) {
			this.style.roll(this.npc, this.npc.getRandom());
		}

		SpeechContext context = SpeechFacts.build(this.npc, listener);
		if (extras != null) {
			extras.accept(context);
		}

		long now = this.npc.world.getWorldTime();
		String line = SpeechEngine.speak(event, context, this.usedLines, now, this.npc.getRandom());
		if (line == null) {
			return null;
		}

		this.learnFrom(context);

		float catchphraseChance = this.npc.personality.has(Quirk.CHATTERBOX) ? 0.2F : 0.1F;
		if (CASUAL.contains(event) && !this.style.getCatchphrase().isEmpty() && this.npc.getRandom().nextFloat() < catchphraseChance
			&& !line.contains(this.style.getCatchphrase())) {
			line = line + " " + this.style.getCatchphrase();
		}

		return this.style.apply(line, this.npc.getRandom());
	}

	/** A line for this event, or the fallback if nothing fits. */
	public String lineOr(String event, @Nullable Entity listener, String fallback, @Nullable Consumer<SpeechContext> extras) {
		String line = this.line(event, listener, extras);
		return line != null ? line : fallback;
	}

	/**
	 * Says a line aloud for this event, if the NPC isn't talking too much. Important lines (being downed,
	 * a friend dying) skip the NPC's own cooldown; taciturn NPCs keep most other reactions to themselves.
	 */
	public boolean bark(String event, @Nullable Entity listener, @Nullable Consumer<SpeechContext> extras, boolean important) {
		long now = this.npc.world.getWorldTime();
		if (!important && (now < this.nextBark || this.npc.personality.has(Quirk.TACITURN) && this.npc.getRandom().nextFloat() < 0.6F)) {
			return false;
		}

		if (now - lastAnyLine < GLOBAL_GAP && now >= lastAnyLine) {
			return false;
		}

		String line = this.line(event, listener, extras);
		if (line == null) {
			return false;
		}

		long cooldown = BARK_COOLDOWN;
		if (this.npc.personality.has(Quirk.CHATTERBOX)) {
			cooldown /= 2;
		}

		this.nextBark = now + cooldown;
		this.sayAloud(line);
		return true;
	}

	/** Puts a line in the chat of every player within earshot. */
	public void sayAloud(String line) {
		lastAnyLine = this.npc.world.getWorldTime();
		for (Player player : this.npc.world.players) {
			if (player.distanceTo(this.npc) <= HEARING_RANGE) {
				player.sendMessage("<" + this.npc.nickname + "> " + line);
			}
		}
	}

	/** Speaking up unprompted: greetings, remarks, chats with other NPCs, and replies to them. */
	public void tick() {
		if ((this.npc.tickCount + this.npc.id) % CHECK_INTERVAL != 0 || this.npc.isDowned()) {
			return;
		}

		long now = this.npc.world.getWorldTime();
		if (this.replyTo != null && now >= this.replyAt) {
			EntityNPC partner = this.replyTo;
			this.replyTo = null;
			if (!partner.removed && partner.distanceTo(this.npc) < CHAT_PARTNER_RANGE * 2) {
				this.npc.setLookTarget(partner, 60);
				this.bark("chat_reply", partner, null, true);
			}

			return;
		}

		if (this.npc.isBusy()) {
			return;
		}

		Player nearest = this.npc.world.getClosestPlayerToEntity(this.npc, IDLE_RANGE);
		if (nearest == null) {
			return;
		}

		if (this.tryGreet(nearest, now) || this.tryChat(now)) {
			return;
		}

		this.tryIdle(nearest, now);
	}

	private boolean tryGreet(Player player, long now) {
		if (player.distanceTo(this.npc) > GREET_RANGE || player.uuid == null || !this.npc.canEntityBeSeen(player)) {
			return false;
		}

		Long last = this.lastGreeted.get(player.uuid);
		if (last != null && now - last < GREET_COOLDOWN && now >= last) {
			return false;
		}

		this.lastGreeted.put(player.uuid, now);
		float opinion = Opinions.of(this.npc, player);
		float chance = 0.35F + 0.3F * this.npc.personality.get(TraitAxis.SOCIABILITY) + (opinion >= 20.0F ? 0.2F : 0.0F)
			+ (this.npc.personality.has(Quirk.CHATTERBOX) ? 0.3F : 0.0F) - (this.npc.personality.has(Quirk.TACITURN) ? 0.3F : 0.0F);
		if (opinion <= Opinions.hostileThreshold(this.npc) || this.npc.getRandom().nextFloat() > chance) {
			return false;
		}

		this.npc.setLookTarget(player, 40);
		return this.bark("greet", player, null, true);
	}

	private boolean tryChat(long now) {
		if (now < this.nextChat) {
			return false;
		}

		float sociability = this.npc.personality.get(TraitAxis.SOCIABILITY);
		if (this.npc.getRandom().nextFloat() > 0.03F * (1.0F + sociability) * this.talkativeness()) {
			return false;
		}

		AABBd area = MathHelper.aabbGrow(this.npc.bb, CHAT_PARTNER_RANGE, 2.0, CHAT_PARTNER_RANGE, new AABBd());
		List<EntityNPC> nearby = this.npc.world.getEntitiesWithinAABB(EntityNPC.class, area);
		for (EntityNPC partner : nearby) {
			if (partner == this.npc || partner.isBusy() || now < partner.voice.nextChat || !this.npc.canEntityBeSeen(partner)) {
				continue;
			}

			float opinion = Opinions.of(this.npc, partner);
			if (opinion <= Opinions.hostileThreshold(this.npc)) {
				continue;
			}

			this.nextChat = now + CHAT_COOLDOWN;
			partner.voice.nextChat = now + CHAT_COOLDOWN;
			this.npc.setLookTarget(partner, 80);
			if (this.bark("chat_open", partner, null, true)) {
				partner.voice.replyTo = this.npc;
				partner.voice.replyAt = now + REPLY_DELAY;
				if (opinion > -20.0F && Opinions.of(partner, this.npc) > -20.0F) {
					this.npc.remember(MemoryType.HAD_A_NICE_CHAT, partner, 1.0F);
					partner.remember(MemoryType.HAD_A_NICE_CHAT, this.npc, 1.0F);
				}

				// Friendly chats spread the words players have taught (the general ones).
				if (opinion >= 20.0F && Opinions.of(partner, this.npc) >= 20.0F && this.npc.getRandom().nextFloat() < WORD_SHARE_CHANCE) {
					WordBook.get(this.npc.world).share(this.npc.getUuid(), this.npc.nickname, partner.getUuid(), this.npc.getRandom());
				}
			}

			return true;
		}

		return false;
	}

	private void tryIdle(Player player, long now) {
		if (now < this.nextIdle) {
			return;
		}

		if (this.nextIdle == 0L || this.npc.getRandom().nextFloat() > 0.04F * this.talkativeness()) {
			if (this.nextIdle == 0L) {
				this.nextIdle = now + this.npc.getRandom().nextInt((int) IDLE_COOLDOWN_SPREAD);
			}

			return;
		}

		this.nextIdle = now + (long) ((IDLE_COOLDOWN_MIN + this.npc.getRandom().nextInt((int) IDLE_COOLDOWN_SPREAD)) / this.talkativeness());
		this.bark("idle", player, null, true);
	}

	/** Chatterboxes speak up about twice as often, the taciturn about a third as often. */
	private float talkativeness() {
		if (this.npc.personality.has(Quirk.CHATTERBOX)) {
			return 2.0F;
		}

		return this.npc.personality.has(Quirk.TACITURN) ? 0.33F : 1.0F;
	}

	/**
	 * Whether the player has heard this NPC talk about one of its loves or hates ("favorite_food", "hated_biome"...).
	 * Until then the profile screen shows a question mark.
	 */
	public boolean isRevealed(String topic) {
		return this.revealed.contains(topic);
	}

	public void revealAll() {
		for (String which : new String[] {"favorite_", "hated_"}) {
			for (String topic : new String[] {"food", "thing", "weather", "biome", "season"}) {
				this.revealed.add(which + topic);
			}
		}
	}

	public void reveal(String topic) {
		this.revealed.add(topic);
	}

	/** Notes which tastes a line gave away, if a player was close enough to hear it. */
	private void learnFrom(SpeechContext context) {
		boolean heard = false;
		for (Player player : this.npc.world.players) {
			heard |= player.distanceTo(this.npc) <= HEARING_RANGE;
		}

		if (!heard) {
			return;
		}

		Tastes tastes = this.npc.personality.tastes;
		for (String topic : context.mentioned()) {
			if (!topic.startsWith("felt:")) {
				this.revealed.add(topic);
				continue;
			}

			// "I love the rain!" while it's raining gives away a favorite only if rain really is the favorite.
			String key = topic.substring("felt:".length(), topic.length() - "_like".length());
			for (Surroundings.Kind kind : Surroundings.Kind.values()) {
				String current = context.fact(kind.id());
				if (!kind.id().equals(key) || current == null) {
					continue;
				}

				if (current.equals(tastes.favorite(kind, true))) {
					this.revealed.add("favorite_" + kind.id());
				} else if (current.equals(tastes.favorite(kind, false))) {
					this.revealed.add("hated_" + kind.id());
				}
			}
		}
	}

	public CompoundTag save() {
		CompoundTag tag = this.style.save();
		tag.putString("Revealed", String.join(",", this.revealed));
		return tag;
	}

	public void load(CompoundTag tag) {
		this.style.load(tag);
		this.revealed.clear();
		for (String topic : tag.getStringOrDefault("Revealed", "").split(",")) {
			if (!topic.isBlank()) {
				this.revealed.add(topic.trim());
			}
		}
	}
}
