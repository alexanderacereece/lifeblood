package lifeblood.mixin;

import net.minecraft.server.MinecraftServer;
import lifeblood.world.GameSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the extra game ticks /lb debug speed asks for, after each normal one, so testing doesn't take real hours. */
@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MinecraftServerMixin {
	@Unique
	private boolean lifeblood$speedingUp;

	@Shadow
	private void doTick() {
	}

	@Inject(method = "doTick", at = @At("TAIL"))
	private void lifeblood$extraTicks(CallbackInfo ci) {
		if (this.lifeblood$speedingUp) {
			return;
		}

		this.lifeblood$speedingUp = true;
		try {
			for (int i = 1; i < GameSpeed.ticksPerTick(); i++) {
				this.doTick();
			}
		} finally {
			this.lifeblood$speedingUp = false;
		}
	}
}
