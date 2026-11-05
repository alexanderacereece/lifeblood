package lifeblood.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.block.Blocks;
import org.junit.jupiter.api.Test;

/**
 * Prefab swaps by convention (docs/building.md): logs and plain planks become the local wood, swappable stone the
 * local stone in the same form; everything else stays as built. Painting (planks colored by the wood, gray to the
 * primary color) needs BTA's block logic, which only exists in a running game, so it's checked in game, not here.
 */
class SwapsTest {
	@Test
	void stoneKeepsItsForm() {
		assertEquals(Blocks.BRICK_BASALT, StoneFamily.BASALT.swap(Blocks.BRICK_GRANITE));
		assertEquals(Blocks.STAIRS_COBBLE_BASALT, StoneFamily.BASALT.swap(Blocks.STAIRS_COBBLE_STONE));
		assertEquals(Blocks.BASALT, StoneFamily.BASALT.swap(Blocks.LIMESTONE));
		assertEquals(StoneFamily.GRANITE, StoneFamily.of(Blocks.SLAB_BRICK_GRANITE));
	}

	@Test
	void aFormTheLocalStoneLacksStaysAsBuilt() {
		// Slate has no cobbled form.
		assertEquals(Blocks.COBBLE_STONE, StoneFamily.SLATE.swap(Blocks.COBBLE_STONE));
	}

	@Test
	void chosenStonesNeverSwap() {
		assertNull(StoneFamily.of(Blocks.SANDSTONE));
		assertEquals(Blocks.SANDSTONE, StoneFamily.BASALT.swap(Blocks.SANDSTONE));
	}

	@Test
	void logsAndPlainPlanksTakeTheLocalWood() {
		assertEquals(Blocks.LOG_PINE, WoodType.PINE.swap(Blocks.LOG_BIRCH, 3).block());
		assertEquals(3, WoodType.PINE.swap(Blocks.LOG_BIRCH, 3).meta());
		assertEquals(WoodType.CHERRY, WoodType.of(Blocks.LOG_CHERRY));
		// Oak's planks are the plain ones, so they stay.
		assertEquals(Blocks.PLANKS_OAK, WoodType.OAK.swap(Blocks.PLANKS_OAK, 0).block());
		assertTrue(WoodType.isPlainPlanks(Blocks.STAIRS_PLANKS_OAK));
		assertFalse(WoodType.isPlainPlanks(Blocks.PLANKS_OAK_PAINTED));
	}
}
