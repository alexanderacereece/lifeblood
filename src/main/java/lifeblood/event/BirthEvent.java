package lifeblood.event;

import net.minecraft.core.entity.Mob;
import lifeblood.entity.EntityNPC;

/**
 * A child was born, to two NPCs or to an NPC and a player. Posted once the child is in the world, named and linked
 * to its parents in the register of people.
 *
 * @param child the newborn
 * @param parent one parent (the one the birth started from)
 * @param otherParent the other parent
 */
public record BirthEvent(EntityNPC child, Mob parent, Mob otherParent) {
}
