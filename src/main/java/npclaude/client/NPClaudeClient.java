package npclaude.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import npclaude.NPClaude;
import npclaude.entity.EntityNPC;
import npclaude.interaction.NpcInteractions;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public class NPClaudeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(NPClaude.MOD_ID),
			dispatcher -> dispatcher.assignRenderer(EntityNPC.class, new NpcRenderer()));

		NpcInteractions.setHandler((npc, player) -> {
			Minecraft mc = Minecraft.getMinecraft();
			if (player == mc.thePlayer) {
				mc.displayScreen(new NpcDialogueScreen(npc, player, npc.getDialogue(player)));
			}
		});
	}
}
