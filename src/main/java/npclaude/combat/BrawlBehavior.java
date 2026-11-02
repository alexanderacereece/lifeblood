package npclaude.combat;

import net.minecraft.core.entity.Mob;
import npclaude.ai.NpcBehavior;
import npclaude.entity.EntityNPC;
import npclaude.social.Brawl;

/**
 * Fists up: walks at the other brawler and punches them. An instinct just below real combat, so a monster turning
 * up calls the brawl off and the NPC deals with that instead.
 */
public class BrawlBehavior implements NpcBehavior {
	public static final String ID = "brawl";
	public static final int PRIORITY = 95;
	private static final double PUNCH_RANGE = 1.8;
	private static final int REPATH_INTERVAL = 10;

	private int repathCooldown;

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
		return npc.brawling.getBrawl() != null;
	}

	@Override
	public void start(EntityNPC npc) {
		this.repathCooldown = 0;
		npc.putAwayWeapon();
	}

	@Override
	public void tick(EntityNPC npc) {
		Brawl brawl = npc.brawling.getBrawl();
		if (brawl == null) {
			return;
		}

		Mob opponent = brawl.opponentOf(npc);
		if (opponent.removed || !opponent.isAlive() || opponent.world != npc.world) {
			brawl.callOff();
			return;
		}

		// Walking off forfeits: a player leaving the brawl loses it.
		if (opponent.distanceTo(npc) > Brawl.FORFEIT_DISTANCE) {
			brawl.forfeit(opponent);
			return;
		}

		npc.setLookTarget(opponent, 10);
		double distance = Math.sqrt(opponent.distanceToSqr(npc));
		if (distance > PUNCH_RANGE) {
			if (--this.repathCooldown <= 0) {
				this.repathCooldown = REPATH_INTERVAL;
				npc.navigator.moveTo(opponent);
			}
		} else {
			npc.navigator.stop();
		}

		npc.tryPunch(opponent);
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
		Brawl brawl = npc.brawling.getBrawl();
		if (brawl != null) {
			// Something more important took over (a real fight); the brawl can't go on.
			brawl.callOff();
		}
	}
}
