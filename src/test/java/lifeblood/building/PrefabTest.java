package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.nbt.tags.CompoundTag;
import java.util.List;
import java.util.Map;
import net.minecraft.core.util.helper.Direction;
import org.junit.jupiter.api.Test;

class PrefabTest {
	/** A 2 wide, 1 high, 2 deep prefab: planks, air, a chest and a door. */
	private static Prefab sample() {
		CompoundTag chest = new CompoundTag();
		chest.putString("id", "minecraft:chest");
		chest.putInt("x", 100);
		return new Prefab("hut", 2, 1, 2, Direction.EAST, List.of("planks", "air", "chest", "door"),
			new short[] {0, 1, 2, 3}, new short[] {0, 0, 5, 0x72}, Map.of(2, chest));
	}

	@Test
	void spotsAreNumberedXThenZThenY() {
		Prefab prefab = sample();
		assertEquals(0, prefab.index(0, 0, 0));
		assertEquals(1, prefab.index(1, 0, 0));
		assertEquals(2, prefab.index(0, 0, 1));
		assertEquals("chest", prefab.blockAt(0, 0, 1));
		assertEquals(5, prefab.metaAt(0, 0, 1));
		assertEquals(100, prefab.tileEntityAt(0, 0, 1).getInteger("x"));
	}

	@Test
	void savesAndLoadsEverything() {
		Prefab loaded = Prefab.load(sample().save());
		assertEquals("hut", loaded.name);
		assertEquals(2, loaded.width);
		assertEquals(1, loaded.height);
		assertEquals(2, loaded.depth);
		assertEquals(Direction.EAST, loaded.front);
		assertEquals(List.of("planks", "air", "chest", "door"), loaded.palette());
		assertEquals("door", loaded.blockAt(1, 0, 1));
		assertEquals(0x72, loaded.metaAt(1, 0, 1));
		assertEquals("minecraft:chest", loaded.tileEntityAt(0, 0, 1).getString("id"));
		assertEquals(Map.of("planks", 1, "air", 1, "chest", 1, "door", 1), loaded.blockCounts());
	}

	@Test
	void refusesBrokenData() {
		CompoundTag tag = sample().save();
		tag.putInt("Width", 3);
		assertThrows(IllegalArgumentException.class, () -> Prefab.load(tag));
	}
}
