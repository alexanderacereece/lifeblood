package lifeblood.health;

import net.minecraft.core.util.helper.MathHelper;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;

/**
 * After being downed, the NPC goes home and stays there until it has recovered, whatever its orders were.
 * An instinct: it sits above every order (follow, wander...) but below combat, which makes a recovering NPC run.
 * The recovery itself is tracked by the NPC, so it survives saving.
 */
public class RecoverBehavior implements NpcBehavior {
	public static final String ID = "recover";
	public static final int PRIORITY = 90;
	private static final double HOME_DISTANCE = 2.0;
	private static final int CHECK_INTERVAL = 20;

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
		return npc.health.isRecovering();
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
		if (!npc.navigator.isMoving() && npc.home.distanceSqr() > HOME_DISTANCE * HOME_DISTANCE) {
			npc.navigator.moveTo(MathHelper.floor(npc.home.getX()), MathHelper.floor(npc.home.getY()), MathHelper.floor(npc.home.getZ()));
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public String describe() {
		return "recovering at home";
	}
}
