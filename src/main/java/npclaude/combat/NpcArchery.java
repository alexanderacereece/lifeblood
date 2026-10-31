package npclaude.combat;

import java.util.List;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.projectile.Projectile;
import net.minecraft.core.entity.projectile.ProjectileArrow;
import net.minecraft.core.entity.projectile.ProjectileArrowFlaming;
import net.minecraft.core.entity.projectile.ProjectileArrowGolden;
import net.minecraft.core.entity.projectile.ProjectileArrowPurple;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;

/**
 * Bow use for NPCs, matching the player's bow (ItemBow.onUse): the same ammo order (worn quiver, golden quiver,
 * flaming, golden, then plain arrows from the inventory), the same arrows, launch speed and spread, and the same bow wear.
 */
public final class NpcArchery {
	/** Launch speed and random spread of a player's bow shot (Projectile's constructor). */
	private static final float ARROW_SPEED = 1.5F;
	private static final float ARROW_SPREAD = 1.0F;
	/** Arrow gravity per tick, used to aim above distant targets. */
	private static final double ARROW_GRAVITY = 0.03;
	/** Ticks between shots, about as fast as a player clicking steadily. */
	private static final int SHOT_COOLDOWN = 20;
	/** Extra room around bystanders when checking the line of fire. */
	private static final double BYSTANDER_MARGIN = 0.3;

	private NpcArchery() {
	}

	public static boolean isBow(ItemStack stack) {
		return stack.getItem() instanceof ItemBow;
	}

	private static boolean isArrow(ItemStack stack, int itemId) {
		return stack.itemID == itemId;
	}

	/** Has a bow and something to shoot with it. */
	public static boolean canShoot(EntityNPC npc) {
		return npc.inventory.findSlot(NpcArchery::isBow) >= 0 && hasAmmo(npc);
	}

	public static boolean hasAmmo(EntityNPC npc) {
		ItemStack chest = npc.getItemInArmorSlot(HumanArmorShape.CHEST);
		if (chest != null && (chest.itemID == Items.ARMOR_QUIVER.id && chest.getMetadata() < chest.getMaxDamage()
			|| chest.itemID == Items.ARMOR_QUIVER_GOLD.id)) {
			return true;
		}

		return npc.inventory.findSlot(stack -> isArrow(stack, Items.AMMO_ARROW.id) || isArrow(stack, Items.AMMO_ARROW_GOLD.id)
			|| isArrow(stack, Items.AMMO_ARROW_FLAMING.id)) >= 0;
	}

	/** Switches the bow into the hand, like a player picking it on the hotbar. */
	public static void readyBow(EntityNPC npc) {
		if (npc.isEating()) {
			return;
		}

		int slot = npc.inventory.findSlot(NpcArchery::isBow);
		if (slot >= 0) {
			npc.inventory.swapWithHand(slot);
		}
	}

	/** Fires one arrow at the target if a bow is held, ammo is available and the shot cooldown is over. */
	public static boolean tryShoot(EntityNPC npc, Entity target) {
		ItemStack bow = npc.getHeldItem();
		if (npc.attackTime > 0 || npc.isEating() || bow == null || !isBow(bow)) {
			return false;
		}

		// Face the target first: the arrow spawns beside the shooter's head based on where it's looking.
		double dx = target.x - npc.x;
		double dz = target.z - npc.z;
		double dy = targetCenterY(target) - (npc.y + npc.getHeadHeight() - 0.1);
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double flightTicks = horizontal / ARROW_SPEED;
		double aimY = dy + 0.5 * ARROW_GRAVITY * flightTicks * flightTicks;
		npc.yRot = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		npc.xRot = (float) (-Math.atan2(aimY, horizontal) * 180.0 / Math.PI);

		Projectile arrow = takeArrow(npc);
		if (arrow == null) {
			return false;
		}

		arrow.setHeading(dx, aimY, dz, ARROW_SPEED, ARROW_SPREAD);
		bow.damageItem(1, npc);
		if (bow.stackSize <= 0) {
			npc.setHeldItem(null);
		}

		npc.world.playSoundAtEntity(null, npc, "random.bow", 0.3F, 1.0F / (npc.getRandom().nextFloat() * 0.4F + 0.8F));
		npc.world.entityJoinedWorld(arrow);
		npc.attackTime = SHOT_COOLDOWN;
		return true;
	}

	/** Uses up ammo in the player's order and makes the matching arrow; null if there is none. */
	@Nullable
	private static Projectile takeArrow(EntityNPC npc) {
		ItemStack chest = npc.getItemInArmorSlot(HumanArmorShape.CHEST);
		if (chest != null && chest.itemID == Items.ARMOR_QUIVER.id && chest.getMetadata() < chest.getMaxDamage()) {
			chest.damageItem(1, npc);
			return new ProjectileArrow(npc.world, npc, true, 0);
		}

		if (chest != null && chest.itemID == Items.ARMOR_QUIVER_GOLD.id) {
			return new ProjectileArrowPurple(npc.world, npc, false);
		}

		if (npc.inventory.consumeOne(stack -> isArrow(stack, Items.AMMO_ARROW_FLAMING.id))) {
			return new ProjectileArrowFlaming(npc.world, npc, true);
		}

		if (npc.inventory.consumeOne(stack -> isArrow(stack, Items.AMMO_ARROW_GOLD.id))) {
			return new ProjectileArrowGolden(npc.world, npc, true);
		}

		if (npc.inventory.consumeOne(stack -> isArrow(stack, Items.AMMO_ARROW.id))) {
			return new ProjectileArrow(npc.world, npc, true, 0);
		}

		return null;
	}

	/**
	 * False if anyone who isn't hostile (allies, neutral players and NPCs, animals) stands in the line of fire
	 * between the NPC and its target.
	 */
	public static boolean hasClearShot(EntityNPC npc, Entity target, List<Mob> hostiles) {
		double x0 = npc.x;
		double y0 = npc.y + npc.getHeadHeight() - 0.1;
		double z0 = npc.z;
		double x1 = target.x;
		double y1 = targetCenterY(target);
		double z1 = target.z;
		AABBd lineBounds = new AABBd(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
		MathHelper.aabbGrow(lineBounds, 1.0, 1.0, 1.0, lineBounds);
		for (Entity entity : npc.world.getEntitiesWithinAABBExcludingEntity(npc, lineBounds)) {
			if (entity instanceof Mob bystander && bystander != target && bystander.isAlive() && !hostiles.contains(bystander)
				&& segmentHitsBox(x0, y0, z0, x1, y1, z1, bystander.bb, BYSTANDER_MARGIN)) {
				return false;
			}
		}

		return true;
	}

	private static double targetCenterY(Entity target) {
		return target.bb.minY() + (target.bb.maxY() - target.bb.minY()) * 0.5;
	}

	/** Slab test: does the segment from (x0,y0,z0) to (x1,y1,z1) pass through the box grown by margin? */
	private static boolean segmentHitsBox(double x0, double y0, double z0, double x1, double y1, double z1, AABBdc box, double margin) {
		double[] start = {x0, y0, z0};
		double[] delta = {x1 - x0, y1 - y0, z1 - z0};
		double[] min = {box.minX() - margin, box.minY() - margin, box.minZ() - margin};
		double[] max = {box.maxX() + margin, box.maxY() + margin, box.maxZ() + margin};
		double enter = 0.0;
		double exit = 1.0;
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(delta[axis]) < 1.0E-9) {
				if (start[axis] < min[axis] || start[axis] > max[axis]) {
					return false;
				}

				continue;
			}

			double t1 = (min[axis] - start[axis]) / delta[axis];
			double t2 = (max[axis] - start[axis]) / delta[axis];
			enter = Math.max(enter, Math.min(t1, t2));
			exit = Math.min(exit, Math.max(t1, t2));
			if (enter > exit) {
				return false;
			}
		}

		return true;
	}
}
