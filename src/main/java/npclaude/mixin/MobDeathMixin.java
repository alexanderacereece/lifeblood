package npclaude.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import npclaude.social.SocialEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets NPCs nearby react to any death: thanking whoever helped, grieving friends, and remembering killers. */
@Mixin(value = Mob.class, remap = false)
public abstract class MobDeathMixin {
	@Inject(method = "onDeath", at = @At("HEAD"))
	private void npclaude$witnessDeath(Entity killedBy, CallbackInfo ci) {
		SocialEvents.onDeath((Mob) (Object) this, killedBy);
	}
}
