package npclaude.client;

import net.fabricmc.api.ClientModInitializer;
import npclaude.NPClaude;
import npclaude.entity.EntityNPC;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public class NPClaudeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(NPClaude.MOD_ID),
			dispatcher -> dispatcher.assignRenderer(EntityNPC.class, new NpcRenderer()));
	}
}
