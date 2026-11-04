package lifeblood.ai.behavior;

import com.mojang.nbt.tags.CompoundTag;
import java.util.UUID;
import net.minecraft.core.entity.player.Player;
import lifeblood.ai.NpcBehavior;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.FollowDistance;
import org.jetbrains.annotations.Nullable;

/** Walks after a player, teleporting to them when left too far behind. */
public class FollowPlayerBehavior implements NpcBehavior {
	public static final String ID = "follow";
	/** Teleports when no path is found and the player is this far away. */
	private static final float TELEPORT_DISTANCE = 12.0F;
	/** Teleports regardless beyond this, since the pathfinder can't plan that far. */
	private static final float FORCE_TELEPORT_DISTANCE = 24.0F;
	private static final int REPATH_INTERVAL = 10;

	@Nullable
	private UUID playerId;
	private int repathCooldown;

	public FollowPlayerBehavior() {
	}

	public FollowPlayerBehavior(UUID playerId) {
		this.playerId = playerId;
	}

	@Nullable
	public Player getPlayer(EntityNPC npc) {
		return this.playerId != null ? npc.world.getPlayerEntityByUUID(this.playerId) : null;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean canStart(EntityNPC npc) {
		return this.getPlayer(npc) != null;
	}

	@Override
	public void start(EntityNPC npc) {
		this.repathCooldown = 0;
	}

	@Override
	public void tick(EntityNPC npc) {
		Player player = this.getPlayer(npc);
		if (player == null) {
			return;
		}

		npc.setLookTarget(player, 20);
		float distance = player.distanceTo(npc);
		// Starts walking once the player is further than the NPC's follow distance, stops once close enough.
		FollowDistance range = npc.following.getDistance();
		if (distance <= range.stop) {
			npc.navigator.stop();
			return;
		}

		if (distance > FORCE_TELEPORT_DISTANCE) {
			npc.navigator.teleportNear(player);
			return;
		}

		if (--this.repathCooldown > 0) {
			return;
		}

		this.repathCooldown = REPATH_INTERVAL;
		if (distance < range.start && !npc.navigator.isMoving()) {
			return;
		}

		// No route, or one that only gets partway (the player climbed somewhere unreachable): catch up by teleporting.
		boolean routed = npc.navigator.moveTo(player);
		if ((!routed || !npc.navigator.isRouteComplete()) && distance > TELEPORT_DISTANCE) {
			npc.navigator.teleportNear(player);
		}
	}

	@Override
	public void stop(EntityNPC npc) {
		npc.navigator.stop();
	}

	@Override
	public void save(CompoundTag tag) {
		if (this.playerId != null) {
			tag.putString("Player", this.playerId.toString());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		String id = tag.getString("Player");
		try {
			this.playerId = id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			this.playerId = null;
		}
	}

	@Override
	public String describe() {
		return "following a player";
	}
}
