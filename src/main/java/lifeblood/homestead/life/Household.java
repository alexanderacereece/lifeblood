package lifeblood.homestead.life;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.container.Container;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.HomesteadRegister;
import lifeblood.work.Containers;
import org.jetbrains.annotations.Nullable;

/**
 * A homesteader's household as far as their day goes: their house (found from their home, which is in it) and its
 * chests, what the chests hold, and which chest to walk to. Only loaded chests count.
 */
public final class Household {
	private Household() {
	}

	/** The homestead someone lives in, or null if their home isn't in one. */
	@Nullable
	public static HomesteadRegister.Homestead of(EntityNPC npc) {
		return HomesteadRegister.get(npc.world).at(npc.home.getX(), npc.home.getY(), npc.home.getZ());
	}

	/** The house's chests that are loaded, each with its position {x, y, z}. */
	public static List<Stored> chests(EntityNPC npc, HomesteadRegister.Homestead homestead) {
		List<Stored> chests = new ArrayList<>();
		for (int[] chest : homestead.chests()) {
			TilePos pos = new TilePos(chest[0], chest[1], chest[2]);
			if (npc.world.isBlockLoaded(pos) && npc.world.getTileEntity(pos) instanceof Container container) {
				chests.add(new Stored(chest, container));
			}
		}

		return chests;
	}

	/** One chest: where it is and what's in it. */
	public record Stored(int[] pos, Container container) {
	}

	/** How many items matching the household's chests hold between them. */
	public static int count(EntityNPC npc, Predicate<ItemStack> matches) {
		HomesteadRegister.Homestead homestead = of(npc);
		if (homestead == null) {
			return 0;
		}

		int count = 0;
		for (Stored chest : chests(npc, homestead)) {
			count += Containers.count(chest.container(), matches);
		}

		return count;
	}

	/** The nearest chest holding something matching, or null. */
	@Nullable
	public static Stored chestWith(EntityNPC npc, Predicate<ItemStack> matches) {
		HomesteadRegister.Homestead homestead = of(npc);
		Stored best = null;
		double bestDistance = Double.MAX_VALUE;
		if (homestead != null) {
			for (Stored chest : chests(npc, homestead)) {
				double distance = npc.distanceToSqr(chest.pos()[0] + 0.5, chest.pos()[1] + 0.5, chest.pos()[2] + 0.5);
				if (Containers.count(chest.container(), matches) > 0 && distance < bestDistance) {
					best = chest;
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	/** The nearest chest with an empty slot, or null if they're all full or there are none. */
	@Nullable
	public static Stored chestWithRoom(EntityNPC npc) {
		HomesteadRegister.Homestead homestead = of(npc);
		Stored best = null;
		double bestDistance = Double.MAX_VALUE;
		if (homestead != null) {
			for (Stored chest : chests(npc, homestead)) {
				double distance = npc.distanceToSqr(chest.pos()[0] + 0.5, chest.pos()[1] + 0.5, chest.pos()[2] + 0.5);
				if (hasRoom(chest.container()) && distance < bestDistance) {
					best = chest;
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	private static boolean hasRoom(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot) == null) {
				return true;
			}
		}

		return false;
	}
}
