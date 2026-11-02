package npclaude.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.world.World;
import npclaude.people.PeopleRegistry;

/**
 * The world systems, run from each world's tick (see the world tick mixin). Each world keeps its own count of
 * ticks, so systems keep their pace even when the time of day is changed or frozen.
 */
public final class WorldSystems {
	private static final List<WorldSystem> SYSTEMS = new ArrayList<>();
	private static final Map<World, Long> TICKS = new WeakHashMap<>();

	static {
		register(PeopleRegistry.UPKEEP);
	}

	private WorldSystems() {
	}

	public static void register(WorldSystem system) {
		SYSTEMS.add(system);
	}

	/** How many ticks this world has run since it was loaded; steady, unlike the time of day, which can jump. */
	public static long ticks(World world) {
		return TICKS.getOrDefault(world, 0L);
	}

	public static void tick(World world) {
		if (world.isClientSide) {
			return;
		}

		long tick = TICKS.merge(world, 1L, Long::sum);
		for (WorldSystem system : SYSTEMS) {
			if (tick % system.interval() == 0) {
				system.tick(world);
			}
		}
	}
}
