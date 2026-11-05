package lifeblood.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.item.model.ItemModelStandard;
import lifeblood.ClientHooks;
import lifeblood.Lifeblood;
import lifeblood.entity.EntityNPC;
import lifeblood.item.NpcItems;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * The client side of the mod, started by Fabric on the client only: renderers, item icons, and the {@link ClientHooks}
 * through which common code opens screens and refreshes textures.
 */
public class LifebloodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(Lifeblood.MOD_ID),
			dispatcher -> dispatcher.assignRenderer(EntityNPC.class, new NpcRenderer()));

		// Item icons: assets/lifeblood/textures/item/<name>.png.
		ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(Lifeblood.MOD_ID),
			dispatcher -> {
				dispatcher.addDispatch(new ItemModelStandard(NpcItems.guardianAmulet, true));
				dispatcher.addDispatch(new ItemModelStandard(NpcItems.wordBook, true));
			});
		ClientHooks.setAppearanceReload(AppearanceTextures::reload);
		ClientHooks.setSkinReload(NpcSkins::reload);
		ClientHooks.setWordBookHandler(player -> {
			Minecraft mc = Minecraft.getMinecraft();
			if (player == mc.thePlayer) {
				mc.displayScreen(new WordBookScreen(player));
			}
		});

		ClientHooks.setHandler((npc, player) -> {
			Minecraft mc = Minecraft.getMinecraft();
			if (player == mc.thePlayer) {
				mc.displayScreen(new NpcDialogueScreen(npc, player, npc.getDialogue(player)));
			}
		});
	}
}
