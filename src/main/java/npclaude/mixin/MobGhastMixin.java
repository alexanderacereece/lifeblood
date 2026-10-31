package npclaude.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.monster.MobGhast;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.player.gamemode.Gamemodes;
import npclaude.combat.NpcTargeting;
import npclaude.entity.EntityNPC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ghasts re-pick their target when they have none or every second; at those moments a visible NPC
 * that is closer than any valid player becomes the target instead. Their fireballs already hurt anything they hit.
 */
@Mixin(value = MobGhast.class, remap = false)
public abstract class MobGhastMixin {
	private static final double NPC_RANGE = 64.0;
	/** Matches the player search range in MobGhast.updateAI. */
	private static final double PLAYER_RANGE = 100.0;

	@Shadow
	private Entity targetedEntity;
	@Shadow
	private int aggroCooldown;

	@Inject(method = "updateAI", at = @At("HEAD"))
	private void npclaude$alsoTargetNpcs(CallbackInfo ci) {
		if (this.targetedEntity != null && this.aggroCooldown > 0) {
			return;
		}

		MobGhast self = (MobGhast) (Object) this;
		EntityNPC npc = NpcTargeting.nearestVisibleNpc(self, NPC_RANGE, NPC_RANGE);
		if (npc == null) {
			return;
		}

		Player player = self.world.getClosestPlayerToEntity(self, PLAYER_RANGE);
		boolean playerValid = player != null && player.gamemode != Gamemodes.CREATIVE && player.gamemode != Gamemodes.SPECTATOR;
		if (!playerValid || npc.distanceToSqr(self) < player.distanceToSqr(self)) {
			this.targetedEntity = npc;
			this.aggroCooldown = 20;
		}
	}
}
