package lifeblood.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.hud.component.HudComponentCrosshair;
import lifeblood.client.NpcDialogueScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the crosshair while talking to an NPC, since the player can't look around then. */
@Mixin(value = HudComponentCrosshair.class, remap = false)
public abstract class HudComponentCrosshairMixin {
	@Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
	private void lifeblood$hideDuringDialogue(CallbackInfoReturnable<Boolean> cir) {
		if (Minecraft.getMinecraft().currentScreen instanceof NpcDialogueScreen) {
			cir.setReturnValue(false);
		}
	}
}
