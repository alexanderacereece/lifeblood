package lifeblood.combat;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import lifeblood.entity.EntityNPC;
import lifeblood.event.AttackedEvent;
import lifeblood.event.LifeEvents;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import lifeblood.social.MemoryType;
import org.jetbrains.annotations.Nullable;

/**
 * Who last attacked the NPC for real, and how it takes being hit: a person who hits it is remembered, and someone it
 * wasn't fighting gets a complaint. Combat reads this for grudges (fighting back against whoever hit it). Brawl punches
 * and a friend's shove are sorted out before this by brawl/NpcBrawling, and never count as attacks.
 */
public final class NpcAttackers {
	private final EntityNPC npc;
	@Nullable
	private Entity lastAttacker;
	private int lastAttackedTick = Integer.MIN_VALUE / 2;

	public NpcAttackers(EntityNPC npc) {
		this.npc = npc;
	}

	@Nullable
	public Entity getLastAttacker() {
		return this.lastAttacker;
	}

	public int ticksSinceAttacked() {
		return this.npc.tickCount - this.lastAttackedTick;
	}

	/** Lets go of wanting to fight back against this attacker (after a brawl with them ends fairly). */
	public void forgive(Entity attacker) {
		if (this.lastAttacker == attacker) {
			this.lastAttacker = null;
		}
	}

	/** Whether this NPC still wants to fight back against someone who hit it recently (not counting brawl punches). */
	public boolean holdsGrudge(Entity mob, int grudgeTicks) {
		return mob == this.lastAttacker && this.ticksSinceAttacked() < grudgeTicks && !this.npc.brawling.isBrawlingWith(mob);
	}

	/** Whether this person is holding something that hits harder than a fist. */
	public boolean isArmed(@Nullable Entity person) {
		ItemStack held = person instanceof Player player ? player.inventory.getCurrentItem() : person instanceof EntityNPC other ? other.getHeldItem() : null;
		return held != null && held.getDamageVsEntity(this.npc) > 1;
	}

	/** It was really attacked by this: remembered, and complained about if it wasn't already fighting them. */
	public void attackedBy(Entity source) {
		this.lastAttacker = source;
		this.lastAttackedTick = this.npc.tickCount;
		LifeEvents.post(new AttackedEvent(this.npc, source));
		if (!(source instanceof Player) && !(source instanceof EntityNPC)) {
			return;
		}

		boolean wasHostile = NpcFactions.regard(this.npc, source) == FactionRelation.HOSTILE;
		this.npc.remember(MemoryType.HIT_ME, source, 1.0F);
		if (!wasHostile && !(this.npc.brain.getActive() instanceof CombatBehavior)) {
			this.npc.voice.bark("hit_by_friend", source, null, false);
		}
	}
}
