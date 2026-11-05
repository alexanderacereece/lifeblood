package lifeblood;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.util.collection.NamespaceID;
import lifeblood.command.LifebloodCommand;
import lifeblood.entity.EntityNPC;
import lifeblood.item.NpcItems;
import lifeblood.appearance.NpcSkinFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * The mod's entry point, started by Fabric on both client and server: loads the settings, registers the NPC entity,
 * the items and the /lb command. Client-only setup is in client/LifebloodClient.
 */
public class Lifeblood implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("lifeblood", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NpcConfig.load();
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommandManager.registerCommand(new LifebloodCommand());
		NpcItems.init();
		LOGGER.info("Lifeblood initialized. NPC skins folder: {}", NpcSkinFiles.getDirectory());
	}

	public void beforeGameStart() {
		EntityDispatcher.getInstance().addMapping(EntityNPC.class, NamespaceID.fromPool(MOD_ID, "npc"), EntityNPC::new);
	}
}
