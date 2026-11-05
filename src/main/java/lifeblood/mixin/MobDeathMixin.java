package lifeblood.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.projectile.Projectile;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posts a {@link DeathEvent} whenever any mob dies (players included), so systems can react: witnesses, family, followers. */
@Mixin(value = Mob.class, remap = false)
public abstract class MobDeathMixin {
	@Inject(method = "onDeath", at = @At("HEAD"))
	private void lifeblood$witnessDeath(Entity killedBy, CallbackInfo ci) {
		Mob victim = (Mob) (Object) this;
		if (!victim.world.isClientSide) {
			Entity killer = killedBy instanceof Projectile projectile && projectile.owner != null ? projectile.owner : killedBy;
			LifeEvents.post(new DeathEvent(victim, killer));
		}
	}
}
