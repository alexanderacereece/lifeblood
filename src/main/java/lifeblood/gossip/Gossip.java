package lifeblood.gossip;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.world.World;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.Persons;
import lifeblood.event.AttackedEvent;
import lifeblood.event.BirthEvent;
import lifeblood.event.BrawlEndedEvent;
import lifeblood.event.DeathEvent;
import lifeblood.event.PartnershipEvent;
import lifeblood.faction.FactionRelation;
import lifeblood.faction.NpcFactions;
import lifeblood.family.Kinship;
import lifeblood.people.PeopleRegistry;
import lifeblood.personality.Quirk;
import lifeblood.social.MemoryType;
import lifeblood.social.Opinions;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;

/**
 * How news starts and what hearing it does. Things that happen (deaths, births, couples changing, attacks, brawls)
 * become news, known at once by everyone who saw it and by the family of those involved, wherever they are; after
 * that it travels by word of mouth (see GossipTopics). Hearing that someone hurt or killed a person lowers your
 * opinion of them by a quarter of what seeing it would, more if the victim was a friend or family, and half again if
 * a liar dressed the story up in the telling.
 */
public final class Gossip {
	/** NPCs this close to something see it happen, and know it first-hand. */
	private static final double WITNESS_RANGE = 24.0;
	/** Hearing about something counts this much of seeing it. */
	static final float HEARSAY = 0.25F;
	/** A story that grew in the telling counts this much more. */
	static final float EXAGGERATED = 1.5F;
	/** An opinion this high makes the victim a friend, as far as hearing about them goes. */
	private static final float FRIEND = 50.0F;

	private Gossip() {
	}

	// What becomes news

	/** An NPC died: killed by someone, or not. Other deaths (monsters, animals, players who respawn) aren't news. */
	static void onDeath(DeathEvent event) {
		if (!(event.victim() instanceof EntityNPC victim)) {
			return;
		}

		Entity killer = event.killer();
		if (killer != null && killer != victim && Persons.isPerson(killer)) {
			spread(victim.world, victim, NewsKind.KILLING, killer, victim, "");
		} else {
			spread(victim.world, victim, NewsKind.DEATH, victim, null, "");
		}
	}

	static void onBirth(BirthEvent event) {
		spread(event.child().world, event.child(), NewsKind.BIRTH, event.parent(), event.otherParent(), event.child().nickname);
	}

	/** A couple started dating, got engaged, married, or split up. */
	static void onPartnership(PartnershipEvent event) {
		NewsKind kind = event.stage() == null ? NewsKind.BREAKUP : switch (event.stage()) {
			case "dating" -> NewsKind.DATING;
			case "engaged" -> NewsKind.ENGAGEMENT;
			default -> NewsKind.WEDDING;
		};
		World world = event.world();
		if (world == null || world.isClientSide) {
			return;
		}

		PeopleRegistry people = PeopleRegistry.get(world);
		News piece = GossipRegister.get(world).report(kind, event.first(), people.nameOf(event.first()), event.second(),
			people.nameOf(event.second()), "", world.getWorldTime(), GossipRegister.freshTicks(world));
		Entity first = find(world, event.first());
		tellFirsthand(world, piece, first != null ? first : find(world, event.second()));
	}

	/** Someone attacked an NPC out of nowhere: not an enemy, and not in the middle of a fight already. */
	static void onAttacked(AttackedEvent event) {
		EntityNPC victim = event.npc();
		Entity attacker = event.attacker();
		if (!Persons.isPerson(attacker) || victim.state.isInCombat() || NpcFactions.regard(victim, attacker) == FactionRelation.HOSTILE) {
			return;
		}

		spread(victim.world, victim, NewsKind.ATTACK, attacker, victim, "");
	}

	static void onBrawlEnded(BrawlEndedEvent event) {
		spread(event.winner().world, event.winner(), NewsKind.BRAWL, event.winner(), event.loser(), "");
	}

	/** Records the news and tells everyone who knows it first-hand. */
	private static void spread(World world, Entity where, NewsKind kind, Entity subject, @Nullable Entity other, String babyName) {
		UUID subjectId = Opinions.idOf(subject);
		if (world.isClientSide || subjectId == null) {
			return;
		}

		UUID otherId = other != null ? Opinions.idOf(other) : null;
		News piece = GossipRegister.get(world).report(kind, subjectId, Opinions.nameOf(subject), otherId,
			other != null ? Opinions.nameOf(other) : "", babyName, world.getWorldTime(), GossipRegister.freshTicks(world));
		tellFirsthand(world, piece, where);
	}

	/** Those involved, the people near where it happened, and the families of those involved know it straight away. */
	private static void tellFirsthand(World world, News piece, @Nullable Entity where) {
		GossipRegister register = GossipRegister.get(world);
		Set<UUID> knowers = new HashSet<>();
		knowers.add(piece.subject());
		if (piece.other() != null) {
			knowers.add(piece.other());
		}

		PeopleRegistry people = PeopleRegistry.get(world);
		knowers.addAll(Kinship.relativesOf(people, piece.subject()));
		if (piece.other() != null) {
			knowers.addAll(Kinship.relativesOf(people, piece.other()));
		}

		if (where != null) {
			AABBd area = MathHelper.aabbGrow(where.bb, WITNESS_RANGE, 8.0, WITNESS_RANGE, new AABBd());
			for (EntityNPC witness : world.getEntitiesWithinAABB(EntityNPC.class, area)) {
				if (witness.isAlive()) {
					knowers.add(witness.getUuid());
				}
			}

			for (Player player : world.players) {
				if (player.uuid != null && player.distanceTo(where) <= WITNESS_RANGE) {
					knowers.add(player.uuid);
				}
			}
		}

		for (UUID knower : knowers) {
			register.hear(piece, knower, null, "", false);
		}
	}

	// Hearing it

	/**
	 * An NPC heard news from someone else: if it reflects badly on whoever it's about, it thinks less of them, by a
	 * quarter of what seeing it would do, more if the victim was its friend or family, and more again if exaggerated.
	 */
	static void heardFromSomeone(EntityNPC hearer, News piece, boolean exaggerated) {
		if (!piece.kind().shameful || piece.other() == null || piece.involves(hearer.getUuid())) {
			return;
		}

		PeopleRegistry people = PeopleRegistry.get(hearer.world);
		Kinship kin = Kinship.of(people, hearer.getUuid(), piece.other());
		boolean family = kin != null && kin.opinion > 0;
		boolean closest = kin == Kinship.SPOUSE || kin == Kinship.CHILD || kin == Kinship.FIANCE;
		boolean friend = regard(hearer, piece.other()) >= FRIEND;
		MemoryType memory = piece.kind() == NewsKind.KILLING
			? closest ? MemoryType.KILLED_MY_SPOUSE_OR_CHILD : family ? MemoryType.KILLED_FAMILY : friend ? MemoryType.KILLED_A_FRIEND
				: MemoryType.HEARD_THEY_KILLED_SOMEONE
			: MemoryType.HEARD_THEY_HURT_SOMEONE;
		float scale = HEARSAY * (exaggerated ? EXAGGERATED : 1.0F) * (piece.kind() == NewsKind.ATTACK && (family || friend) ? 2.0F : 1.0F);
		boolean bond = regard(hearer, piece.subject()) >= FRIEND;
		hearer.memories.add(memory, piece.subject(), piece.subjectName(), scale, hearer.personality, bond, hearer.world.getWorldTime());
	}

	/** Whether this teller dresses a story up when passing it on: liars do, half the time. */
	static boolean embellishes(EntityNPC teller) {
		return teller.personality.has(Quirk.LIAR) && teller.getRandom().nextFloat() < 0.5F;
	}

	/** What an NPC thinks of someone who may be far away or dead: in full if they're here, else from memories and family. */
	static float regard(EntityNPC npc, UUID person) {
		Entity here = find(npc.world, person);
		if (here != null) {
			return Opinions.of(npc, here);
		}

		Kinship kin = Kinship.of(PeopleRegistry.get(npc.world), npc.getUuid(), person);
		return npc.memories.opinionOf(person, npc.world.getWorldTime()) + (kin != null ? kin.opinion : 0);
	}

	/** The person with this id, if they're in this world right now. */
	@Nullable
	static Entity find(World world, UUID person) {
		EntityNPC npc = PeopleRegistry.get(world).loaded(world, person);
		if (npc != null) {
			return npc;
		}

		for (Player player : world.players) {
			if (person.equals(player.uuid)) {
				return player;
			}
		}

		return null;
	}
}
