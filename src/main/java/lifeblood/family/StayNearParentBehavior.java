package lifeblood.family;

import java.util.UUID;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.people.PeopleRegistry;

/**
 * A child keeps close to a parent who's around (an NPC or a player), toddling after them when they wander off and
 * pottering about nearby otherwise. An instinct, so every child has it; it stops mattering once they grow up, or
 * when no parent is near enough to see.
 */
public class StayNearParentBehavior implements NpcBehavior {
	public static final String ID = "stay_near_parent";
	public static final int PRIORITY = 20;
	private static final double FIND_RANGE = 48.0;
	private static final double CATCH_UP_DISTANCE = 5.0;
	private static final int CHECK_INTERVAL = 20;
	private static final int POTTER_RADIUS = 3;

	private int checkCooldown;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public int getPriority() {
		return PRIORITY;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return npc.life.isChild() && findParent(npc) != null;
	}

	@Override
	public void start(EntityNPC npc) {
		this.checkCooldown = 0;
	}

	@Override
	public void tick(EntityNPC npc) {
		if (--this.checkCooldown > 0) {
			return;
		}

		this.checkCooldown = CHECK_INTERVAL;
		Mob parent = findParent(npc);
		if (parent == null) {
			return;
		}

		npc.setLookTarget(parent, 20);
		if (parent.distanceTo(npc) > CATCH_UP_DISTANCE) {
			npc.navigator.moveTo(parent);
		} else if (!npc.navigator.isMoving() && npc.getRandom().nextInt(4) == 0) {
			int x = MathHelper.floor(parent.x) + npc.getRandom().nextInt(POTTER_RADIUS * 2 + 1) - POTTER_RADIUS;
			int z = MathHelper.floor(parent.z) + npc.getRandom().nextInt(POTTER_RADIUS * 2 + 1) - POTTER_RADIUS;
			npc.navigator.moveTo(x, MathHelper.floor(parent.y), z);
		}
	}

	/** The nearest parent close enough to stay with. */
	private static Mob findParent(EntityNPC npc) {
		Mob nearest = null;
		for (UUID id : PeopleRegistry.get(npc.world).parentsOf(npc.getUuid())) {
			Mob parent = Romance.findPerson(npc.world, id);
			if (parent != null && parent.isAlive() && parent.distanceTo(npc) <= FIND_RANGE
				&& (nearest == null || parent.distanceTo(npc) < nearest.distanceTo(npc))) {
				nearest = parent;
			}
		}

		return nearest;
	}
}
