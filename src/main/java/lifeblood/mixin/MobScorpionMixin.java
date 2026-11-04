package lifeblood.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.monster.MobScorpion;
import lifeblood.combat.NpcTargeting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scorpions pick their own targets and are only hostile in the dark; same rule applies to NPCs. */
@Mixin(value = MobScorpion.class, remap = false)
public abstract class MobScorpionMixin {
	@Inject(method = "findPlayerToAttack", at = @At("RETURN"), cancellable = true)
	private void lifeblood$alsoTargetNpcs(CallbackInfoReturnable<Entity> cir) {
		Mob self = (Mob) (Object) this;
		if (self.calcBrightness(1.0F) < 0.5F) {
			cir.setReturnValue(NpcTargeting.closerTarget(self, cir.getReturnValue()));
		}
	}
}
