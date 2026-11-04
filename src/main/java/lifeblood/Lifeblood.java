package lifeblood;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.net.command.CommandManager;
import net.minecraft.core.util.collection.NamespaceID;
import lifeblood.command.NpcCommand;
import lifeblood.entity.EntityNPC;
import lifeblood.item.NpcItems;
import lifeblood.skin.NpcSkinFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.util.dependency.Key;

public class Lifeblood implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("lifeblood", true);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NpcConfig.load();
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::beforeGameStart);
		CommandManager.registerCommand(new NpcCommand());
		NpcItems.init();
		LOGGER.info("Lifeblood initialized. NPC skins folder: {}", NpcSkinFiles.getDirectory());
	}

	public void beforeGameStart() {
		EntityDispatcher.getInstance().addMapping(EntityNPC.class, NamespaceID.fromPool(MOD_ID, "npc"), EntityNPC::new);
	}
}
