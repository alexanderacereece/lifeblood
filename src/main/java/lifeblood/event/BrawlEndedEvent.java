package lifeblood.event;

import net.minecraft.core.entity.Mob;

/**
 * A brawl ended with a winner: the loser gave in, or walked away (forfeit). Brawls called off without a winner post
 * nothing.
 *
 * @param winner who won
 * @param loser who gave in or walked off
 * @param forfeit whether the loser walked away rather than giving in
 */
public record BrawlEndedEvent(Mob winner, Mob loser, boolean forfeit) {
}
