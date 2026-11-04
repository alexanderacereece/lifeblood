package lifeblood.world;

import net.minecraft.core.world.World;

/**
 * Something that runs for a whole world on its own clock, whether or not any NPC is nearby: keeping registers
 * tidy now, and later elections, wars, trade between towns, and time passing for people who aren't loaded.
 */
public interface WorldSystem {
	/** How often it runs, in ticks. */
	int interval();

	/** Runs on the side that runs the game, never on a multiplayer client. */
	void tick(World world);
}
