package npclaude;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.util.collection.NamespaceID;
import npclaude.command.NpcCommand;
import npclaude.entity.EntityNPC;
import npclaude.skin.NpcSkinFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

public class NPClaude implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("npclaude", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommandManager.registerCommand(new NpcCommand());
		LOGGER.info("NPClaude initialized. NPC skins folder: {}", NpcSkinFiles.getDirectory());
	}

	public void beforeGameStart() {
		EntityDispatcher.getInstance().addMapping(EntityNPC.class, NamespaceID.getPermanent(MOD_ID, "npc"), EntityNPC::new);
	}
}
