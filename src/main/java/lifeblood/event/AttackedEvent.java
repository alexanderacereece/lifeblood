package lifeblood.event;

import net.minecraft.core.entity.Entity;
import lifeblood.entity.EntityNPC;

/**
 * An NPC was attacked for real and hurt: not a brawl punch or a friend's shove (those are sorted out first).
 *
 * @param npc who was hit
 * @param attacker who hit them: the archer rather than the arrow
 */
public record AttackedEvent(EntityNPC npc, Entity attacker) {
}
