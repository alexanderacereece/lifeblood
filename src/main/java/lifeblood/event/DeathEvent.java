package lifeblood.event;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Someone or something died: any mob, players and NPCs included, just before it's removed.
 *
 * @param victim who died
 * @param killer who killed it: the archer rather than the arrow; null if nobody did (a fall, lava)
 */
public record DeathEvent(Mob victim, @Nullable Entity killer) {
}
