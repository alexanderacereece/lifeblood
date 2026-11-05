package lifeblood.family;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.people.PeopleRegistry;
import lifeblood.personality.Quirk;
import lifeblood.social.MemoryType;
import lifeblood.social.Memory;
import lifeblood.social.Opinions;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;
import lifeblood.world.Calendar;

/**
 * How couples form and change, for NPCs with each other and with players. Anyone can fall for anyone their
 * attraction allows who isn't blood family; the romantic quirk makes it come sooner.
 *
 * Stages (opinions on both sides, the NPC side for a player): dating once both are at 70 or more; engaged after
 * a year and a half of dating with both at 80 or more; married after a year engaged, still at 80. A partner whose
 * opinion sinks too low (under 30 dating, under 10 engaged or married) breaks it off. Times are in in-game years
 * (see Calendar), each standing for a real year, so courtships take as long as real ones.
 */
public final class Romance {
	public static final float DATING_OPINION = 70.0F;
	public static final float ENGAGED_OPINION = 80.0F;
	public static final float DATING_BREAKUP = 30.0F;
	public static final float COMMITTED_BREAKUP = 10.0F;
	/** How long a couple dates before an engagement, in in-game years. */
	public static final float DATING_YEARS = 1.5F;
	/** How long an engagement lasts before the wedding, in in-game years. */
	public static final float ENGAGED_YEARS = 1.0F;
	/** How often each NPC's romance is checked ({@link #tick}), in ticks: once a minute. */
	public static final int CHECK_INTERVAL = 1200;
	private static final double COURTING_RANGE = 16.0;
	private static final double WEDDING_GUEST_RANGE = 24.0;
	/** Per check (once a minute), the chance a ready NPC makes a move; tripled for romantics. */
	private static final float ASK_CHANCE = 0.05F;
	private static final float PROPOSE_CHANCE = 0.3F;

	public enum Request {
		ASK_OUT, PROPOSE
	}

	private Romance() {
	}

	/** Records an NPC in the family registry (and keeps its name current). */
	public static PeopleRegistry.Person register(EntityNPC npc) {
		return PeopleRegistry.get(npc.world).person(npc.getUuid(), npc.nickname, npc.getGender(), false);
	}

	public static PeopleRegistry.Person register(Player player) {
		return PeopleRegistry.get(player.world).person(player.uuid, player.username, null, true);
	}

	@Nullable
	public static NpcGender genderOf(Entity entity) {
		return entity instanceof EntityNPC npc ? npc.getGender() : null;
	}

	/** Whether this NPC could be with them at all: not blood family, and someone it can be attracted to. */
	public static boolean couldFallFor(EntityNPC npc, Entity other) {
		UUID id = Opinions.idOf(other);
		if (id == null || id.equals(npc.getUuid()) || npc.life.isChild() || other instanceof EntityNPC otherNpc && otherNpc.life.isChild()) {
			return false;
		}

		Kinship kin = Kinship.of(PeopleRegistry.get(npc.world), npc.getUuid(), id);
		return (kin == null || !kin.blood) && npc.personality.attraction().allows(npc.getGender(), genderOf(other));
	}

	public static float daysTogether(PeopleRegistry.Partnership partnership, long now) {
		return (now - partnership.since) / (float) Memory.TICKS_PER_DAY;
	}

	/** How long a couple has been at its current stage, in in-game years. */
	public static float yearsTogether(PeopleRegistry.Partnership partnership, World world, long now) {
		return (now - partnership.since) / (float) Calendar.ticksPerYear(world);
	}

	/** Whether a couple has dated long enough to get engaged. */
	public static boolean datedLongEnough(PeopleRegistry.Partnership partnership, World world, long now) {
		return yearsTogether(partnership, world, now) >= DATING_YEARS;
	}

	/** Whether a couple has been engaged long enough to marry. */
	public static boolean engagedLongEnough(PeopleRegistry.Partnership partnership, World world, long now) {
		return yearsTogether(partnership, world, now) >= ENGAGED_YEARS;
	}

	/** A loaded person by id: an NPC in the world or a player. */
	@Nullable
	public static Mob findPerson(World world, UUID id) {
		Player player = world.getPlayerEntityByUUID(id);
		if (player != null) {
			return player;
		}

		return PeopleRegistry.get(world).loaded(world, id);
	}

	/** Once a minute: keep a relationship going (or end it), or look for one. */
	public static void tick(EntityNPC npc) {
		register(npc);
		if (npc.life.isChild()) {
			return;
		}

		PeopleRegistry family = PeopleRegistry.get(npc.world);
		PeopleRegistry.Partnership partnership = family.partnershipOf(npc.getUuid());
		if (partnership != null) {
			tendRelationship(npc, family, partnership);
		} else if (!npc.isBusy() && !npc.health.isRecovering()) {
			lookForLove(npc, family);
		}
	}

	private static void tendRelationship(EntityNPC npc, PeopleRegistry family, PeopleRegistry.Partnership partnership) {
		Mob partner = findPerson(npc.world, partnership.other(npc.getUuid()));
		if (partner == null) {
			return;
		}

		long now = npc.world.getWorldTime();
		float mine = Opinions.of(npc, partner);
		float limit = partnership.stage == PeopleRegistry.Stage.DATING ? DATING_BREAKUP : COMMITTED_BREAKUP;
		if (mine < limit) {
			breakUp(npc, partner, family);
			return;
		}

		// Between two NPCs, one of them (the one with the smaller id) moves things along, so it happens once.
		if (partner instanceof EntityNPC other) {
			if (npc.getUuid().compareTo(other.getUuid()) > 0 || npc.distanceTo(other) > COURTING_RANGE) {
				return;
			}

			float theirs = Opinions.of(other, npc);
			float chance = PROPOSE_CHANCE * (npc.personality.has(Quirk.ROMANTIC) ? 2.0F : 1.0F);
			if (partnership.stage == PeopleRegistry.Stage.DATING && datedLongEnough(partnership, npc.world, now) && mine >= ENGAGED_OPINION
				&& theirs >= ENGAGED_OPINION && npc.getRandom().nextFloat() < chance) {
				npc.voice.bark("propose", other, null, true);
				getEngaged(npc, other, family);
			} else if (partnership.stage == PeopleRegistry.Stage.ENGAGED && engagedLongEnough(partnership, npc.world, now) && mine >= ENGAGED_OPINION
				&& theirs >= ENGAGED_OPINION && npc.getRandom().nextFloat() < chance) {
				wed(npc, other, family);
			} else if (partnership.stage == PeopleRegistry.Stage.MARRIED && mine >= ENGAGED_OPINION && theirs >= ENGAGED_OPINION
				&& npc.getRandom().nextFloat() < Children.birthChance(npc.world) && Children.canHaveChild(npc.world, family, npc.getUuid(), other.getUuid(), now)) {
				Children.born(npc, other);
			}
		} else if (partner instanceof Player player && npc.distanceTo(player) <= COURTING_RANGE && npc.social.getRomanceRequest() == null) {
			// With a player, the NPC can't just decide: it asks next time they talk.
			if (partnership.stage == PeopleRegistry.Stage.MARRIED && mine >= ENGAGED_OPINION && npc.getRandom().nextFloat() < Children.birthChance(npc.world)
				&& Children.canHaveChild(npc.world, family, npc.getUuid(), player.uuid, now)) {
				Children.born(npc, player);
				return;
			}

			if (partnership.stage == PeopleRegistry.Stage.DATING && datedLongEnough(partnership, npc.world, now) && mine >= ENGAGED_OPINION
				&& npc.getRandom().nextFloat() < PROPOSE_CHANCE * 0.3F) {
				npc.social.setRomanceRequest(Request.PROPOSE, player);
				npc.voice.bark("want_to_talk", player, null, false);
			}
		}
	}

	/** Single and looking: asks out someone nearby it's grown close to. */
	private static void lookForLove(EntityNPC npc, PeopleRegistry family) {
		float chance = ASK_CHANCE * (npc.personality.has(Quirk.ROMANTIC) ? 3.0F : 1.0F);
		if (npc.getRandom().nextFloat() >= chance) {
			return;
		}

		AABBd area = MathHelper.aabbGrow(npc.bb, COURTING_RANGE, 4.0, COURTING_RANGE, new AABBd());
		List<Entity> nearby = npc.world.getEntitiesWithinAABBExcludingEntity(npc, area);
		for (Entity entity : nearby) {
			UUID id = Opinions.idOf(entity);
			if (id == null || family.partnershipOf(id) != null || !couldFallFor(npc, entity) || Opinions.of(npc, entity) < DATING_OPINION) {
				continue;
			}

			if (entity instanceof EntityNPC other && !other.isBusy()) {
				npc.voice.bark("ask_out", other, null, true);
				if (couldFallFor(other, npc) && Opinions.of(other, npc) >= DATING_OPINION) {
					startDating(npc, other, family);
					other.voice.bark("ask_out_yes", npc, null, true);
				} else {
					npc.remember(MemoryType.TURNED_ME_DOWN, other, 1.0F);
					other.voice.bark("ask_out_no", npc, null, true);
				}

				return;
			}

			if (entity instanceof Player player && npc.social.getRomanceRequest() == null && player.getGamemode().hasHostileMobs()) {
				npc.social.setRomanceRequest(Request.ASK_OUT, player);
				npc.voice.bark("want_to_talk", player, null, false);
				return;
			}
		}
	}

	public static void startDating(EntityNPC npc, Mob other, PeopleRegistry family) {
		if (other instanceof Player player) {
			register(player);
		}

		if (family.startDating(npc.getUuid(), Opinions.idOf(other), npc.world.getWorldTime())) {
			npc.remember(MemoryType.STARTED_DATING, null, 1.0F);
			if (other instanceof EntityNPC otherNpc) {
				otherNpc.remember(MemoryType.STARTED_DATING, null, 1.0F);
			}
		}
	}

	public static void getEngaged(EntityNPC npc, Mob other, PeopleRegistry family) {
		family.setStage(npc.getUuid(), PeopleRegistry.Stage.ENGAGED, npc.world.getWorldTime());
		npc.remember(MemoryType.GOT_ENGAGED, null, 1.0F);
		if (other instanceof EntityNPC otherNpc) {
			otherNpc.remember(MemoryType.GOT_ENGAGED, null, 1.0F);
			otherNpc.voice.bark("accept_proposal", npc, null, true);
		}
	}

	/** The wedding: the couple is married, and everyone nearby enjoys it. */
	public static void wed(EntityNPC npc, Mob other, PeopleRegistry family) {
		family.setStage(npc.getUuid(), PeopleRegistry.Stage.MARRIED, npc.world.getWorldTime());
		npc.remember(MemoryType.GOT_MARRIED, null, 1.0F);
		npc.voice.bark("wedding", other, null, true);
		if (other instanceof EntityNPC otherNpc) {
			otherNpc.remember(MemoryType.GOT_MARRIED, null, 1.0F);
		}

		AABBd area = MathHelper.aabbGrow(npc.bb, WEDDING_GUEST_RANGE, 6.0, WEDDING_GUEST_RANGE, new AABBd());
		for (EntityNPC guest : npc.world.getEntitiesWithinAABB(EntityNPC.class, area)) {
			if (guest != npc && guest != other && guest.isAlive()) {
				guest.remember(MemoryType.WENT_TO_A_WEDDING, null, 1.0F);
				if (guest.getRandom().nextFloat() < 0.4F) {
					guest.voice.bark("wedding_guest", npc, null, false);
				}
			}
		}
	}

	/** This NPC ends it. Both feel it; the one left behind remembers who did it. */
	public static void breakUp(EntityNPC npc, Mob partner, PeopleRegistry family) {
		family.breakUp(npc.getUuid());
		npc.voice.bark("break_up", partner, null, true);
		if (partner instanceof EntityNPC other) {
			other.remember(MemoryType.BROKE_UP_WITH_ME, npc, 1.0F);
		}
	}
}
