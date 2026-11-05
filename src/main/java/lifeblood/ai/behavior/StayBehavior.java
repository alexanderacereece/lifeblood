package lifeblood.ai.behavior;

import net.minecraft.core.util.helper.MathHelper;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;

/** Stands at the NPC's home position, walking back if pushed away. */
public class StayBehavior implements NpcBehavior {
	public static final String ID = "stay";
	private static final double RETURN_DISTANCE = 2.0;
	private static final int CHECK_INTERVAL = 20;

	private int checkCooldown;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return true;
	}

	@Override
	public void start(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public void tick(EntityNPC npc) {
		if (--this.checkCooldown > 0) {
			return;
		}

		this.checkCooldown = CHECK_INTERVAL;
		if (!npc.navigator.isMoving() && npc.home.distanceSqr() > RETURN_DISTANCE * RETURN_DISTANCE) {
			npc.navigator.moveTo(MathHelper.floor(npc.home.getX()), MathHelper.floor(npc.home.getY()), MathHelper.floor(npc.home.getZ()));
		}
	}

	@Override
	public String describe() {
		return "staying";
	}
}
