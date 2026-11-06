package lifeblood.mixin;

import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.generate.chunk.perlin.overworld.ChunkDecoratorOverworld;
import lifeblood.homestead.Homesteads;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets generated homesteads go up once the overworld has decorated a new chunk (trees and snow included). */
@Mixin(value = ChunkDecoratorOverworld.class, remap = false)
public abstract class ChunkDecoratorOverworldMixin {
	@Shadow
	@Final
	private World world;

	@Inject(method = "decorate", at = @At("TAIL"))
	private void lifeblood$generateHomesteads(Chunk chunk, CallbackInfo ci) {
		Homesteads.decorate(this.world, chunk);
	}
}
