package lifeblood.combat;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemBow;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.DamageType;
import lifeblood.brawl.Brawl;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcPart;
import lifeblood.inventory.NpcInventory;
import lifeblood.personality.Quirk;

/**
 * Hand-to-hand fighting by the player's rules: drawing the best weapon carried, hitting with it or with a bare fist,
 * and putting the weapon away once things are calm. The melee counterpart of {@link NpcArchery}. Deciding when to fight
 * is {@link CombatBehavior}'s job (or the brawl's); this is how the blows land. Melee skill will plug in here.
 */
public final class NpcMelee implements NpcPart {
	/** How long after a fight the weapon stays out, in ticks (15 seconds). */
	private static final int WEAPON_AWAY_TICKS = 300;
	/** Ticks between hits; players can click faster, monsters wait 20. */
	private static final int COOLDOWN = 15;
	/** How far a hit reaches, in blocks, measured centre to centre. */
	private static final double REACH = 2.5;

	private final EntityNPC npc;
	/** The last tick the NPC was fighting or brawling, for putting the weapon away afterwards. */
	private int lastFightTick;

	public NpcMelee(EntityNPC npc) {
		this.npc = npc;
	}

	/** Once there's been no fighting for a while, puts a weapon in hand away, like a player would. Paranoid NPCs keep theirs out. */
	@Override
	public void tick() {
		if (this.npc.brain.getActive() instanceof CombatBehavior || this.npc.brawling.isBrawling()) {
			this.lastFightTick = this.npc.tickCount;
		} else if (this.npc.tickCount - this.lastFightTick > WEAPON_AWAY_TICKS && (this.npc.tickCount + this.npc.id) % 20 == 0
			&& !this.npc.eating.isEating() && !this.npc.personality.has(Quirk.PARANOID)) {
			this.putAwayWeapon();
		}
	}

	/**
	 * Moves the hardest-hitting item from the inventory into the hand, swapping places with what was held,
	 * like a player switching to their sword. Does nothing if the hand already holds the best option.
	 */
	public void readyWeapon() {
		if (this.npc.eating.isEating()) {
			return;
		}

		ItemStack held = this.npc.getHeldItem();
		int bestDamage = held != null ? held.getDamageVsEntity(this.npc) : 1;
		int bestSlot = -1;
		for (int i = 0; i < NpcInventory.PACK_SIZE; i++) {
			ItemStack stack = this.npc.inventory.getItem(NpcInventory.PACK_START + i);
			if (stack != null && !(stack.getItem() instanceof ItemBow) && stack.getDamageVsEntity(this.npc) > bestDamage) {
				bestDamage = stack.getDamageVsEntity(this.npc);
				bestSlot = NpcInventory.PACK_START + i;
			}
		}

		if (bestSlot >= 0) {
			this.npc.inventory.swapWithHand(bestSlot);
		}
	}

	/** True if anything carried hits harder than a bare fist (bows don't count). */
	public boolean hasWeapon() {
		return this.npc.inventory.findSlot(stack -> !(stack.getItem() instanceof ItemBow) && stack.getDamageVsEntity(this.npc) > 1) >= 0;
	}

	/** Moves a held weapon into the pack (if there's room), like putting it away for a fistfight. */
	public void putAwayWeapon() {
		ItemStack held = this.npc.getHeldItem();
		if (held != null && (held.getDamageVsEntity(this.npc) > 1 || held.getItem() instanceof ItemBow) && this.npc.inventory.addToPack(held)) {
			this.npc.setHeldItem(null);
		}
	}

	/**
	 * A bare-fisted punch, like a player's with an empty hand, for brawls. Never takes a player below 1 health, and
	 * a player beaten down to their yield point loses.
	 */
	public boolean tryPunch(Mob target) {
		if (!this.readyToHit(target)) {
			return false;
		}

		this.startSwing();
		if (target instanceof Player && target.getHealth() <= 1) {
			return false;
		}

		boolean hit = target.hurt(this.npc, 1, DamageType.COMBAT);
		Brawl brawl = this.npc.brawling.getBrawl();
		if (hit && brawl != null && target instanceof Player) {
			brawl.checkYield(target);
		}

		return hit;
	}

	/** Hits the target with the held item, like a player's attack, if it's in reach and the cooldown is over. */
	public boolean tryAttack(Entity target) {
		if (!this.readyToHit(target)) {
			return false;
		}

		this.startSwing();
		ItemStack weapon = this.npc.getHeldItem();
		int damage = weapon != null ? weapon.getDamageVsEntity(target) : 1;
		boolean hit = target.hurt(this.npc, damage, DamageType.COMBAT);
		if (hit && weapon != null && weapon.isItemStackDamageable()) {
			weapon.damageItem(1, this.npc);
			if (weapon.stackSize <= 0) {
				this.npc.setHeldItem(null);
			}
		}

		return hit;
	}

	/** The cooldown is over, the hands are free, and the target is in reach and level with it. */
	private boolean readyToHit(Entity target) {
		return this.npc.attackTime <= 0 && !this.npc.eating.isEating() && target.distanceToSqr(this.npc) <= REACH * REACH
			&& target.bb.maxY > this.npc.bb.minY && target.bb.minY < this.npc.bb.maxY;
	}

	private void startSwing() {
		this.npc.attackTime = COOLDOWN;
		this.npc.swingArm();
	}

	@Override
	public String saveKey() {
		return "Melee";
	}

	@Override
	public void save(CompoundTag tag) {
		// Nothing lasting: a reloaded NPC simply puts its weapon away on the usual schedule.
	}

	@Override
	public void load(CompoundTag tag) {
	}
}
