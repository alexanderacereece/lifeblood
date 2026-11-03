package npclaude.mixin;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;
import npclaude.ownership.BlockOwners;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blocks a player places by using an item on a block are theirs. */
@Mixin(value = ItemStack.class, remap = false)
public abstract class ItemStackMixin {
	@Inject(method = "useItemOnBlock", at = @At("HEAD"))
	private void npclaude$beginPlacing(Player player, World world, TilePosc blockPos, Side side, double xHit, double yHit,
		CallbackInfoReturnable<Boolean> cir) {
		BlockOwners.beginPlacing(player.uuid);
	}

	@Inject(method = "useItemOnBlock", at = @At("RETURN"))
	private void npclaude$endPlacing(Player player, World world, TilePosc blockPos, Side side, double xHit, double yHit,
		CallbackInfoReturnable<Boolean> cir) {
		BlockOwners.endPlacing();
	}
}
