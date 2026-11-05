package lifeblood.entity;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import lifeblood.combat.CombatBehavior;
import org.jetbrains.annotations.Nullable;

/**
 * What an NPC is up to right now, for any system to ask: whether it's busy, fighting someone, badly hurt, who it's
 * following, who attacked it lately. Systems in lower layers ask here rather than reaching into the parts of higher
 * ones (npc.following, npc.attackers), so the questions they depend on stay few, named and stable while the parts
 * behind them change. A new question goes here only when a lower layer genuinely needs it; code in the same layer or
 * above uses the parts directly.
 */
public final class NpcState {
	/** Below this share of health, the NPC is badly hurt: it says so, and killing its attacker saves its life. */
	private static final float BADLY_HURT_HEALTH = 0.3F;

	private final EntityNPC npc;

	NpcState(EntityNPC npc) {
		this.npc = npc;
	}

	/** Too occupied to start a conversation: fighting, brawling, talking to someone, or lying on the ground. */
	public boolean isBusy() {
		return this.npc.conversation.isTalking() || this.npc.health.isDowned() || this.npc.brawling.isBrawling() || this.isInCombat();
	}

	/** Whether it's fighting for real right now (fight or flight), against anyone. */
	public boolean isInCombat() {
		return this.npc.brain.getActive() instanceof CombatBehavior;
	}

	/** Whether it's in a fight with this mob right now (its current target). */
	public boolean isFighting(@Nullable Entity mob) {
		return this.npc.brain.getActive() instanceof CombatBehavior combat && combat.getCurrentTarget() == mob;
	}

	/** Whether this mob attacked it for real within the last so many ticks (brawl punches don't count). */
	public boolean wasAttackedBy(@Nullable Entity mob, int withinTicks) {
		return mob != null && this.npc.attackers.getLastAttacker() == mob && this.npc.attackers.ticksSinceAttacked() < withinTicks;
	}

	public boolean isBadlyHurt() {
		return this.npc.getHealth() < this.npc.getMaxHealth() * BADLY_HURT_HEALTH;
	}

	/** The player it's following, or null. */
	@Nullable
	public Player leader() {
		return this.npc.following.getLeader();
	}
}
