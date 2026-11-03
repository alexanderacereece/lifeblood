package npclaude.mixin;

import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.pos.ChunkTilePosc;
import npclaude.ownership.BlockOwners;
import npclaude.ownership.ChunkOwners;
import npclaude.ownership.OwnedChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every chunk its ownership records, and keeps them up to date as blocks change. */
@Mixin(value = Chunk.class, remap = false)
public abstract class ChunkMixin implements OwnedChunk {
	@Unique
	private final ChunkOwners npclaude$owners = new ChunkOwners();

	@Override
	public ChunkOwners npclaude$owners() {
		return this.npclaude$owners;
	}

	@Inject(method = "setBlockIdData(Lnet/minecraft/core/world/pos/ChunkTilePosc;IIZ)Z", at = @At("HEAD"))
	private void npclaude$trackOwner(ChunkTilePosc pos, int id, int data, boolean raw, CallbackInfoReturnable<Boolean> cir) {
		if (pos.inBounds()) {
			Chunk chunk = (Chunk) (Object) this;
			BlockOwners.onBlockChanging(chunk, pos.x(), pos.y(), pos.z(), chunk.getBlockId(pos), id);
		}
	}
}
