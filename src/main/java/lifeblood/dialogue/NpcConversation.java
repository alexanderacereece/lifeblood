package lifeblood.dialogue;

import net.minecraft.core.entity.player.Player;
import lifeblood.combat.CombatBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import org.jetbrains.annotations.Nullable;

/**
 * Who the NPC is talking to right now, if anyone. While a conversation is open the NPC stands still and faces the
 * player; it ends when the player walks away, when the NPC is hurt, or a moment after the last screen closes. Also
 * turns a player away when the NPC is in no state to chat (mid-fight, or set on fighting them).
 */
public final class NpcConversation {
	/** A conversation ends if the player walks further away than this, in blocks. */
	private static final float TALK_DISTANCE = 8.0F;
	/** How long a conversation outlasts its screen closing, for the next screen to pick it up, in ticks (half a second). */
	private static final int HANDOFF_TICKS = 10;
	/** At least this long between "not now, I'm fighting" lines when clicked mid-fight, in ticks (1.5 seconds). */
	private static final int REFUSAL_COOLDOWN = 30;

	private final EntityNPC npc;
	@Nullable
	private Player talkingTo;
	/** When a conversation whose screen closed ends, unless another of its screens opens first; 0 if none. */
	private int endsAt;
	private int lastRefusalTick = -REFUSAL_COOLDOWN;

	public NpcConversation(EntityNPC npc) {
		this.npc = npc;
	}

	/** Ends the conversation once the player has walked off or its screens have been closed for a moment. */
	public void update() {
		if (this.talkingTo != null && (this.talkingTo.removed || this.talkingTo.distanceTo(this.npc) > TALK_DISTANCE
			|| this.endsAt > 0 && this.npc.tickCount >= this.endsAt)) {
			this.end();
		}
	}

	@Nullable
	public Player getTalkingTo() {
		return this.talkingTo;
	}

	public boolean isTalking() {
		return this.talkingTo != null;
	}

	/** A conversation screen with this player opened. */
	public void setTalkingTo(@Nullable Player player) {
		this.talkingTo = player;
		this.endsAt = 0;
	}

	/**
	 * A conversation screen with this player closed. The NPC waits a moment before going back to what it was doing,
	 * so moving between the conversation's screens (inventory, profile, typing a word) doesn't let it walk off.
	 */
	public void endSoon(Player player) {
		if (this.talkingTo == player) {
			this.endsAt = this.npc.tickCount + HANDOFF_TICKS;
		}
	}

	/** Ends it now (the NPC was hurt, knocked down, or pulled into a brawl). */
	public void end() {
		this.talkingTo = null;
		this.endsAt = 0;
	}

	/**
	 * Whether the NPC won't talk to this player now: mid-fight, mid-brawl, or set on fighting them. If so it says so
	 * (not too often), and the click is used up.
	 */
	public boolean refuses(Player player) {
		CombatBehavior combat = this.npc.brain.getActive() instanceof CombatBehavior active ? active : null;
		boolean hostile = NpcFactions.regard(this.npc, player) == FactionRelation.HOSTILE;
		boolean brawling = this.npc.brawling.isBrawling();
		if (combat == null && !hostile && !brawling) {
			return false;
		}

		if (this.npc.tickCount - this.lastRefusalTick >= REFUSAL_COOLDOWN) {
			this.lastRefusalTick = this.npc.tickCount;
			boolean fightingYou = combat != null && combat.getCurrentTarget() == player || this.npc.brawling.isBrawlingWith(player);
			this.npc.voice.bark("busy_fighting", player, context -> context.fact("fighting_you", fightingYou ? "yes" : "no")
				.fact("in_combat", combat != null || brawling ? "yes" : "no")
				.fact("brawling", brawling ? "yes" : "no"), true);
		}

		return true;
	}
}
