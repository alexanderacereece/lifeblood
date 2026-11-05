package lifeblood.following;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import lifeblood.ai.NpcBehaviors;
import lifeblood.entity.EntityNPC;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;

/**
 * The follow order, and followers heading home when the player they follow dies.
 */
public final class FollowingSetup {
	private FollowingSetup() {
	}

	/** Called once at startup, from lifeblood.Systems. */
	public static void register() {
		NpcBehaviors.register(FollowPlayerBehavior.ID, FollowPlayerBehavior::new);
		LifeEvents.listen(DeathEvent.class, event -> {
			if (event.victim() instanceof Player player) {
				// A player who dies loses their followers: they head home rather than waiting for them to come back.
				for (Entity entity : player.world.entities) {
					if (entity instanceof EntityNPC npc && npc.isAlive() && npc.following.getLeader() == player) {
						npc.voice.bark("leader_died", player, null, true);
						npc.following.stop();
					}
				}
			}
		});
	}
}
