package lifeblood.combat;

import net.minecraft.core.entity.monster.MobSlime;
import net.minecraft.core.util.helper.DamageType;
import lifeblood.entity.EntityNPC;

/** Damage that monsters deal by touch, which vanilla only applies to players. */
public final class MonsterContact {
	private MonsterContact() {
	}

	/** Mirrors MobSlime.playerTouch: bigger slimes hurt anyone they bump into, for damage equal to their size. */
	public static void slimeTouch(MobSlime slime, EntityNPC npc) {
		int size = slime.getSlimeSize();
		if (size > 1 && slime.canEntityBeSeen(npc) && slime.distanceTo(npc) < 0.6 * size && npc.hurt(slime, size, DamageType.COMBAT)) {
			slime.world.playSoundAtEntity(null, slime, "mob.slimeattack", 1.0F, (npc.getRandom().nextFloat() - npc.getRandom().nextFloat()) * 0.2F + 1.0F);
		}
	}
}
