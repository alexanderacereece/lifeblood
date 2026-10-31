package npclaude.mixin;

import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.monster.MobSlime;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.world.World;
import npclaude.combat.NpcTargeting;
import npclaude.entity.EntityNPC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slimes hop toward the nearest player; when an NPC is closer they hop toward it instead,
 * using the same movement as vanilla MobSlime.updateAI. (Contact damage to NPCs lives in EntityNPC.)
 */
@Mixin(value = MobSlime.class, remap = false)
public abstract class MobSlimeMixin extends Mob {
	/** Matches the player search range in MobSlime.updateAI. */
	private static final double HUNT_RANGE = 16.0;

	@Shadow
	public float squish;
	@Shadow
	private int jumpDelay;

	protected MobSlimeMixin(World world) {
		super(world);
	}

	@Shadow
	public abstract int getSlimeSize();

	@Inject(method = "updateAI", at = @At("HEAD"), cancellable = true)
	private void npclaude$huntNpcs(CallbackInfo ci) {
		EntityNPC npc = NpcTargeting.nearestVisibleNpc(this, HUNT_RANGE, HUNT_RANGE);
		if (npc == null) {
			return;
		}

		Player player = this.world.getClosestPlayerToEntity(this, HUNT_RANGE);
		if (player != null && player.getGamemode().hasHostileMobs() && player.distanceToSqr(this) <= npc.distanceToSqr(this)) {
			return;
		}

		this.tryToDespawn();
		this.lookAt(npc, 10.0F, 20.0F);
		if (this.onGround && this.jumpDelay-- <= 0) {
			this.jumpDelay = (this.random.nextInt(20) + 10) / 3;
			this.isJumping = true;
			if (this.getSlimeSize() > 1) {
				this.world.playSoundAtEntity(null, this, "mob.slime", this.getSoundVolume(),
					((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
			}

			this.squish = 1.0F;
			this.moveStrafing = 1.0F - this.random.nextFloat() * 2.0F;
			this.moveForward = this.getSlimeSize();
		} else {
			this.isJumping = false;
			if (this.onGround) {
				this.moveStrafing = this.moveForward = 0.0F;
			}
		}

		ci.cancel();
	}
}
