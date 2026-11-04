package lifeblood.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.monster.MobMonster;
import lifeblood.combat.NpcTargeting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Monsters (zombies, skeletons, creepers, snowmen...) hunt NPCs as well as players. */
@Mixin(value = MobMonster.class, remap = false)
public abstract class MobMonsterMixin {
	@Inject(method = "findPlayerToAttack", at = @At("RETURN"), cancellable = true)
	private void lifeblood$alsoTargetNpcs(CallbackInfoReturnable<Entity> cir) {
		cir.setReturnValue(NpcTargeting.closerTarget((Mob) (Object) this, cir.getReturnValue()));
	}
}
