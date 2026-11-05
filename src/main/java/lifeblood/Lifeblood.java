package lifeblood;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.util.collection.NamespaceID;
import lifeblood.appearance.NpcSkinFiles;
import lifeblood.command.LifebloodCommand;
import lifeblood.entity.EntityNPC;
import lifeblood.item.NpcItems;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * The mod's entry point, started by Fabric on both client and server: loads the settings, registers the NPC entity,
 * the items and the /lb command. Client-only setup is in client/LifebloodClient.
 */
public class Lifeblood implements ModInitializer {
	@Override
	public void onInitialize() {
		NpcConfig.load();
		Systems.registerAll();
		CommonEvents.BEFORE_GAME_START.listen(Key.of(Mod.ID), this::beforeGameStart);
		CommandManager.registerCommand(new LifebloodCommand());
		NpcItems.init();
		Mod.LOG.info("Lifeblood initialized. NPC skins folder: {}", NpcSkinFiles.getDirectory());
	}

	public void beforeGameStart() {
		EntityDispatcher.getInstance().addMapping(EntityNPC.class, NamespaceID.fromPool(Mod.ID, "npc"), EntityNPC::new);
	}
}
