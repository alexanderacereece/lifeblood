package lifeblood.work;

import java.util.List;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.entity.EntityNPC;
import org.joml.primitives.AABBd;

/**
 * Picking up items lying close by, as a player does by walking over them: only once their pickup delay is over, only
 * what fits in the pack, with the pickup sound. NPCs only pick up while working, so they don't hoover up a player's
 * dropped things on a stroll.
 */
public final class ItemPickup {
	/** How close an item must be to be picked up, as the player's pickup box, in blocks. */
	public static final double REACH = 1.5;

	private ItemPickup() {
	}

	/** Picks up every item within reach that fits; returns how many stacks it took whole or in part. */
	public static int pickUpNearby(EntityNPC npc) {
		AABBd area = MathHelper.aabbGrow(npc.bb, REACH, 0.5, REACH, new AABBd());
		List<EntityItem> items = npc.world.getEntitiesWithinAABB(EntityItem.class, area);
		int taken = 0;
		for (EntityItem item : items) {
			if (item.removed || item.pickupDelay > 0 || item.item == null) {
				continue;
			}

			ItemStack stack = item.item;
			if (npc.inventory.addToPack(stack.copy())) {
				npc.world.playSoundAtEntity(null, npc, "item.pickup", 0.2F, ((npc.getRandom().nextFloat() - npc.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
				item.remove();
				taken++;
			}
		}

		return taken;
	}

	/** The nearest item lying within a radius of the NPC that it could carry, or null; for walking over to it. */
	public static EntityItem nearest(EntityNPC npc, double radius) {
		AABBd area = MathHelper.aabbGrow(npc.bb, radius, 3.0, radius, new AABBd());
		EntityItem best = null;
		double bestDistance = Double.MAX_VALUE;
		for (EntityItem item : npc.world.getEntitiesWithinAABB(EntityItem.class, area)) {
			double distance = npc.distanceToSqr(item);
			if (!item.removed && item.item != null && distance < bestDistance) {
				best = item;
				bestDistance = distance;
			}
		}

		return best;
	}
}
