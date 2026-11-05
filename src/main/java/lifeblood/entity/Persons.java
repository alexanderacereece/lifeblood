package lifeblood.entity;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

/**
 * Telling people (players and NPCs) apart from creatures, and finding who is really behind an attack. Everything that
 * treats people differently from mobs (opinions, memories, factions, brawls) asks here, so "person" means the same
 * everywhere.
 */
public final class Persons {
	private Persons() {
	}

	/** Whether this is a person: a player or an NPC, as opposed to an animal or a monster. */
	public static boolean isPerson(@Nullable Entity entity) {
		return entity instanceof Player || entity instanceof EntityNPC;
	}

	/** Who is behind a hit: the archer rather than the arrow. Anything else is its own source. */
	@Nullable
	public static Entity behind(@Nullable Entity attacker) {
		return attacker instanceof Projectile projectile && projectile.owner != null ? projectile.owner : attacker;
	}
}
