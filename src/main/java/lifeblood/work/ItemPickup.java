package lifeblood.work;

import java.util.List;
import net.minecraft.core.entity.EntityItem;
import net.minecraft.core.entity.projectile.ProjectileArrow;
import net.minecraft.core.entity.projectile.ProjectileArrowFlaming;
import net.minecraft.core.entity.projectile.ProjectileArrowGolden;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.entity.EntityNPC;
import org.joml.primitives.AABBd;

/**
 * Picking up items lying close by, and arrows of its own that landed, as a player does by walking over them: only once their pickup delay is over, only
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

	/**
	 * Picks up arrows of its own lying in the ground within reach, as a player picks up the arrows they shot (only those
	 * that count as the shooter's, and only once they've landed). Returns how many.
	 */
	public static int pickUpArrows(EntityNPC npc) {
		AABBd area = MathHelper.aabbGrow(npc.bb, REACH, 0.5, REACH, new AABBd());
		int taken = 0;
		for (ProjectileArrow arrow : npc.world.getEntitiesWithinAABB(ProjectileArrow.class, area)) {
			if (arrow.removed || !arrow.isGrounded() || !arrow.arrowBelongsToPlayer() || arrow.owner != npc) {
				continue;
			}

			if (npc.inventory.addToPack(new ItemStack(arrowItem(arrow)))) {
				npc.world.playSoundAtEntity(null, npc, "item.pickup", 0.2F, ((npc.getRandom().nextFloat() - npc.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
				arrow.remove();
				taken++;
			}
		}

		return taken;
	}

	/** The nearest arrow of its own lying in the ground within a radius, or null; for walking over to it. */
	public static ProjectileArrow nearestArrow(EntityNPC npc, double radius) {
		AABBd area = MathHelper.aabbGrow(npc.bb, radius, 4.0, radius, new AABBd());
		ProjectileArrow best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ProjectileArrow arrow : npc.world.getEntitiesWithinAABB(ProjectileArrow.class, area)) {
			double distance = npc.distanceToSqr(arrow);
			if (!arrow.removed && arrow.isGrounded() && arrow.arrowBelongsToPlayer() && arrow.owner == npc && distance < bestDistance) {
				best = arrow;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** The arrow item a landed arrow gives back, by its kind: golden, flaming or plain. */
	private static Item arrowItem(ProjectileArrow arrow) {
		if (arrow instanceof ProjectileArrowGolden) {
			return Items.AMMO_ARROW_GOLD;
		}

		return arrow instanceof ProjectileArrowFlaming ? Items.AMMO_ARROW_FLAMING : Items.AMMO_ARROW;
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
