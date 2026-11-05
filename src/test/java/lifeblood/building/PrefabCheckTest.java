package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.minecraft.core.util.helper.Direction;
import org.junit.jupiter.api.Test;

class PrefabCheckTest {
	@Test
	void shortNamesDropTheNamespace() {
		assertEquals("log_oak", PrefabCheck.shortName("minecraft:block/log_oak"));
		assertEquals("stone", PrefabCheck.shortName("mymod:stone"));
		assertEquals("planks", PrefabCheck.shortName("planks"));
	}

	@Test
	void contentsListMostCommonFirstWithoutAir() {
		Prefab prefab = new Prefab("box", 3, 1, 2, Direction.SOUTH,
			List.of("minecraft:block/air", "minecraft:block/log_oak", "minecraft:block/grass"),
			new short[] {0, 0, 1, 2, 2, 2}, new short[6], Map.of());
		assertEquals(List.of(Map.entry("grass", 3), Map.entry("log_oak", 1)), List.copyOf(PrefabCheck.contents(prefab).entrySet()));
	}

	@Test
	void anAllAirBoxHoldsNothing() {
		Prefab prefab = new Prefab("empty", 1, 1, 1, Direction.SOUTH, List.of("minecraft:block/air"), new short[1], new short[1], Map.of());
		assertEquals(Map.of(), PrefabCheck.contents(prefab));
	}
}
