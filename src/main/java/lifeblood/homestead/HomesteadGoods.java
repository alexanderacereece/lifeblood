package lifeblood.homestead;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.block.entity.TileEntityChest;
import net.minecraft.core.block.entity.TileEntityFurnace;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.building.Prefab;
import lifeblood.building.PrefabPlacer;
import lifeblood.building.StoneFamily;
import lifeblood.building.Swaps;
import lifeblood.building.TerrainFit;
import lifeblood.building.WoodType;

/**
 * What a homestead's chests and furnaces hold when it's generated: what the household would have after a few days or
 * weeks of living there, and nothing they couldn't have. Food they'd grow, gather, hunt or cook; seeds; worn wood and
 * stone tools; leftovers from building in the local wood and stone; a little coal and iron from the digging any
 * settler does. No treasure. It's theirs, so taking it is theft.
 */
public final class HomesteadGoods {
	/** The share of a tool's durability used up, at least and at most: they've been working with them. */
	private static final float MIN_WEAR = 0.2F;
	private static final float MAX_WEAR = 0.75F;
	/** The chance they've found a little iron. */
	private static final float IRON_CHANCE = 0.3F;
	/** The chance they hunt with a bow and keep one at home. */
	private static final float BOW_CHANCE = 0.35F;

	private HomesteadGoods() {
	}

	/** Fills the chests and furnaces of a placed homestead for a household of this many people. */
	public static void stock(World world, Prefab prefab, TerrainFit.Site site, Swaps swaps, int people, Random random) {
		List<TileEntityChest> chests = new ArrayList<>();
		List<TileEntityFurnace> furnaces = new ArrayList<>();
		TilePos pos = new TilePos();
		for (int spot : prefab.tileEntities().keySet()) {
			int x = spot % prefab.width;
			int z = spot / prefab.width % prefab.depth;
			int y = spot / (prefab.width * prefab.depth);
			PrefabPlacer.spotPosition(prefab, x, y, z, site.x(), site.y(), site.z(), site.turns(), pos);
			TileEntity tileEntity = world.getTileEntity(pos);
			if (tileEntity instanceof TileEntityChest chest) {
				chests.add(chest);
			} else if (tileEntity instanceof TileEntityFurnace furnace) {
				furnaces.add(furnace);
			}
		}

		List<ItemStack> goods = goods(swaps, Math.max(1, people), random);
		// Shared out round the chests, a stack at a time, each into a random free slot.
		for (int i = 0; i < goods.size() && !chests.isEmpty(); i++) {
			TileEntityChest chest = chests.get(i % chests.size());
			int slot = random.nextInt(chest.getContainerSize());
			for (int tries = 0; tries < chest.getContainerSize() && chest.getItem(slot) != null; tries++) {
				slot = (slot + 1) % chest.getContainerSize();
			}

			if (chest.getItem(slot) == null) {
				chest.setItem(slot, goods.get(i));
			}
		}

		// A furnace keeps a little fuel by it.
		for (TileEntityFurnace furnace : furnaces) {
			furnace.setItem(1, new ItemStack(Items.COAL, 1 + random.nextInt(4)));
		}
	}

	/** The household's goods, more for more people. */
	private static List<ItemStack> goods(Swaps swaps, int people, Random random) {
		List<ItemStack> goods = new ArrayList<>();
		// Food: bread from their wheat, and something gathered, hunted or fished.
		add(goods, Items.FOOD_BREAD, people, 1, 3, random);
		add(goods, Items.WHEAT, people, 1, 4, random);
		add(goods, Items.SEEDS_WHEAT, 1, 4, 14, random);
		maybe(goods, 0.5F, Items.FOOD_APPLE, people, 0, 2, random);
		maybe(goods, 0.5F, random.nextBoolean() ? Items.FOOD_PORKCHOP_COOKED : Items.FOOD_VENISON_COOKED, people, 1, 2, random);
		maybe(goods, 0.3F, Items.FOOD_FISH_COOKED, people, 1, 2, random);
		maybe(goods, 0.4F, Items.EGG_CHICKEN, 1, 1, 5, random);
		maybe(goods, 0.4F, Items.LEATHER, 1, 1, 4, random);
		maybe(goods, 0.4F, Items.FEATHER_CHICKEN, 1, 1, 6, random);
		maybe(goods, 0.3F, Items.STRING, 1, 1, 3, random);
		// A rod made from sticks and string, as anyone living by water might.
		if (random.nextFloat() < 0.5F) {
			goods.add(worn(Items.TOOL_FISHINGROD, random));
		}

		maybe(goods, 0.3F, Items.BONE, 1, 1, 3, random);

		// Leftovers from building, in the wood and stone the house was built from.
		WoodType wood = swaps.wood() != null ? swaps.wood() : WoodType.OAK;
		goods.add(new ItemStack(wood.log, 2 + random.nextInt(12)));
		WoodType.Placed planks = wood.swap(Blocks.PLANKS_OAK, 0);
		goods.add(new ItemStack(planks.block(), 4 + random.nextInt(20), planks.meta()));
		goods.add(new ItemStack(Items.STICK, 2 + random.nextInt(10)));
		StoneFamily stone = swaps.stone();
		goods.add(new ItemStack(stone != null ? stone.swap(Blocks.COBBLE_STONE) : Blocks.COBBLE_STONE, 8 + random.nextInt(32)));
		goods.add(new ItemStack(Blocks.TORCH_COAL, 2 + random.nextInt(10)));
		goods.add(new ItemStack(Items.COAL, 2 + random.nextInt(8)));
		maybe(goods, 0.4F, Items.FLINT, 1, 1, 3, random);
		maybe(goods, IRON_CHANCE, Items.INGOT_IRON, 1, 1, 3, random);

		// Worn tools: what they work the land with.
		goods.add(worn(random.nextBoolean() ? Items.TOOL_HOE_STONE : Items.TOOL_HOE_WOOD, random));
		goods.add(worn(Items.TOOL_AXE_STONE, random));
		goods.add(worn(Items.TOOL_PICKAXE_STONE, random));
		if (random.nextBoolean()) {
			goods.add(worn(Items.TOOL_SHOVEL_STONE, random));
		}

		if (random.nextFloat() < 0.3F) {
			goods.add(worn(Items.TOOL_SWORD_STONE, random));
		}

		// A bow (sticks and string) with a quiver's worth of arrows they made, for some.
		if (random.nextFloat() < BOW_CHANCE) {
			goods.add(worn(Items.TOOL_BOW, random));
			goods.add(new ItemStack(Items.AMMO_ARROW, 6 + random.nextInt(11)));
		}

		return goods;
	}

	/** A stack of this, from min to max per person (or in all, for perPerson 1), if it comes to any. */
	private static void add(List<ItemStack> goods, Item item, int perPerson, int min, int max, Random random) {
		int count = 0;
		for (int i = 0; i < perPerson; i++) {
			count += min + random.nextInt(max - min + 1);
		}

		if (count > 0) {
			ItemStack stack = new ItemStack(item, count);
			stack.stackSize = Math.min(count, item.getItemStackLimit(stack));
			goods.add(stack);
		}
	}

	private static void maybe(List<ItemStack> goods, float chance, Item item, int perPerson, int min, int max, Random random) {
		if (random.nextFloat() < chance) {
			add(goods, item, perPerson, min, max, random);
		}
	}

	/** A tool that has seen some use. */
	private static ItemStack worn(Item tool, Random random) {
		ItemStack stack = new ItemStack(tool, 1);
		int max = stack.getMaxDamage();
		if (max > 0) {
			stack.setMetadata((int) (max * (MIN_WEAR + random.nextFloat() * (MAX_WEAR - MIN_WEAR))));
		}

		return stack;
	}
}
