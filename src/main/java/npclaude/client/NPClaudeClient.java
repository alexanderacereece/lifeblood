package npclaude.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.item.model.ItemModelStandard;
import npclaude.NPClaude;
import npclaude.entity.EntityNPC;
import npclaude.interaction.NpcInteractions;
import npclaude.item.NpcItems;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public class NPClaudeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(NPClaude.MOD_ID),
			dispatcher -> dispatcher.assignRenderer(EntityNPC.class, new NpcRenderer()));

		// The amulet's icon: assets/npclaude/textures/item/guardian_amulet.png.
		ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(NPClaude.MOD_ID),
			dispatcher -> dispatcher.addDispatch(new ItemModelStandard(NpcItems.guardianAmulet, true)));

		NpcInteractions.setHandler((npc, player) -> {
			Minecraft mc = Minecraft.getMinecraft();
			if (player == mc.thePlayer) {
				mc.displayScreen(new NpcDialogueScreen(npc, player, npc.getDialogue(player)));
			}
		});
	}
}
