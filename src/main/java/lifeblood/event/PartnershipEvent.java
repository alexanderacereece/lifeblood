package lifeblood.event;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Two people's relationship changed: they started dating, got engaged, married, or split up. Posted by the register of
 * people when it records the change, however it came about (romance, a player's choice, a command).
 *
 * @param first one partner
 * @param second the other
 * @param stage "dating", "engaged" or "married"; null when they split up
 */
public record PartnershipEvent(UUID first, UUID second, @Nullable String stage) {
}
