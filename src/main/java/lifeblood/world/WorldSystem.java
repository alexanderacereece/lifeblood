package lifeblood.world;

import java.util.function.Consumer;
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

	/** A system that runs this logic every so many ticks. */
	static WorldSystem every(int interval, Consumer<World> tick) {
		return new WorldSystem() {
			@Override
			public int interval() {
				return interval;
			}

			@Override
			public void tick(World world) {
				tick.accept(world);
			}
		};
	}
}
