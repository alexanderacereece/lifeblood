package npclaude.mixin;

import net.minecraft.core.world.World;
import npclaude.world.WorldSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the mod's world systems once per world tick, after the world's own upkeep. */
@Mixin(value = World.class, remap = false)
public abstract class WorldTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void npclaude$tickWorldSystems(CallbackInfo ci) {
		WorldSystems.tick((World) (Object) this);
	}
}
