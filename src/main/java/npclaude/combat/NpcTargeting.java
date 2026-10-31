package npclaude.combat;

import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/** Lets hostile mobs pick NPCs as targets, the same way they pick players. */
public final class NpcTargeting {
	/** Matches the 16-block range monsters use to find players. */
	public static final double MONSTER_SIGHT_RANGE = 16.0;

	private NpcTargeting() {
	}

	/** Returns whichever is closer to the monster: its current target or the nearest NPC it can see. */
	@Nullable
	public static Entity closerTarget(Mob monster, @Nullable Entity current) {
		EntityNPC npc = nearestVisibleNpc(monster, MONSTER_SIGHT_RANGE, 4.0);
		if (npc == null) {
			return current;
		}

		return current == null || npc.distanceToSqr(monster) < current.distanceToSqr(monster) ? npc : current;
	}

	@Nullable
	public static EntityNPC nearestVisibleNpc(Mob monster, double range, double verticalRange) {
		AABBd area = new AABBd(monster.x, monster.y, monster.z, monster.x, monster.y, monster.z);
		MathHelper.aabbGrow(area, range, verticalRange, range, area);
		List<EntityNPC> npcs = monster.world.getEntitiesWithinAABB(EntityNPC.class, area);
		EntityNPC nearest = null;
		double nearestDistance = range * range;
		for (EntityNPC npc : npcs) {
			double distance = npc.distanceToSqr(monster);
			if (npc.isAlive() && distance < nearestDistance && monster.canEntityBeSeen(npc)) {
				nearest = npc;
				nearestDistance = distance;
			}
		}

		return nearest;
	}
}
