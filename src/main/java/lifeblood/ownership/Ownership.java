package lifeblood.ownership;

import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.world.World;
import lifeblood.family.Kinship;
import lifeblood.people.PeopleRegistry;
import lifeblood.social.Opinions;

/**
 * Who shares what someone owns: themself, their husband or wife, their parents and their children. That holds for
 * players and NPCs alike, so a player married into a family can open their spouse's inventory and chests, and an
 * NPC family shares its house. Everyone else needs to be given things.
 */
public final class Ownership {
	private Ownership() {
	}

	public static boolean shares(World world, UUID owner, UUID user) {
		if (owner.equals(user)) {
			return true;
		}

		Kinship kin = Kinship.of(PeopleRegistry.get(world), owner, user);
		return kin == Kinship.SPOUSE || kin == Kinship.PARENT || kin == Kinship.CHILD;
	}

	/** Whether this entity shares what the owner entity owns. */
	public static boolean shares(Entity owner, Entity user) {
		UUID ownerId = Opinions.idOf(owner);
		UUID userId = Opinions.idOf(user);
		return ownerId != null && userId != null && shares(owner.world, ownerId, userId);
	}
}
