package lifeblood.mixin;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.chunk.ChunkLoaderLegacy;
import lifeblood.ownership.OwnedChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Saves and loads each chunk's ownership records along with its blocks (every chunk format goes through here). */
@Mixin(value = ChunkLoaderLegacy.class, remap = false)
public abstract class ChunkLoaderMixin {
	@Inject(method = "storeChunkInCompound", at = @At("TAIL"))
	private static void lifeblood$saveOwners(Chunk chunk, World world, CompoundTag tag, CallbackInfo ci) {
		((OwnedChunk) chunk).lifeblood$owners().save(tag);
	}

	@Inject(method = "loadChunkIntoWorldFromCompound", at = @At("RETURN"))
	private static void lifeblood$loadOwners(World world, CompoundTag tag, CallbackInfoReturnable<Chunk> cir) {
		((OwnedChunk) cir.getReturnValue()).lifeblood$owners().load(tag);
	}
}
