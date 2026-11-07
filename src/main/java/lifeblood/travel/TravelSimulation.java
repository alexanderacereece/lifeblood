package lifeblood.travel;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.entity.EntityNPC;
import lifeblood.event.DeathEvent;
import lifeblood.event.LifeEvents;
import lifeblood.world.Calendar;
import lifeblood.world.WorldSystem;
import org.jetbrains.annotations.Nullable;

/**
 * Trips going on out of sight (a WorldSystem): each traveler in TravelRegister walks on toward where they're going at a
 * walking pace by day and rests by night, stays as long as they meant to (what they find or suffer there worked out by
 * the trip's kind), and heads home. Where a traveler's road brings them near a player, or they get home to a loaded
 * house, they're made real again just as they were, with what they've found. A death out there is a death like any
 * other: the family grieves and the news travels.
 */
public final class TravelSimulation {
	/** How often the travelers are moved along, in ticks. */
	private static final int INTERVAL = 20;
	/** A traveler's pace out of sight, in blocks per tick: a little under the player's walking speed, for the way's twists. */
	private static final double PACE = 0.15;
	/** How near a player a traveler's road must bring them to be made real again, in blocks: inside the handover distance. */
	private static final double MATERIALIZE_DISTANCE = NpcOuting.HANDOVER_DISTANCE - 16.0;
	/** How near a player a traveler who's got home must be to be put back in their house, in blocks. */
	private static final double HOME_DISTANCE = 128.0;
	/** The largest step the simulation takes at once, in ticks (an in-game hour), so day and night are told apart. */
	private static final long STEP = 1000L;
	private static final int DAY_FROM = 6;
	private static final int DAY_UNTIL = 20;

	public static final WorldSystem SYSTEM = WorldSystem.every(INTERVAL, TravelSimulation::tick);

	private TravelSimulation() {
	}

	private static void tick(World world) {
		if (world.isClientSide) {
			return;
		}

		TravelRegister register = TravelRegister.get(world);
		long now = world.getWorldTime();
		for (TravelRegister.Traveler traveler : new ArrayList<>(register.all())) {
			advance(world, register, traveler, now);
		}
	}

	/** Carries a traveler's trip on to now, then makes them real again if they're where a player is, or home. */
	private static void advance(World world, TravelRegister register, TravelRegister.Traveler traveler, long now) {
		Outing outing = traveler.outing;
		Random random = world.rand;
		while (traveler.simulatedTo < now) {
			long step = Math.min(STEP, now - traveler.simulatedTo);
			long at = traveler.simulatedTo;
			traveler.simulatedTo += step;
			switch (outing.stage) {
				case PACKING -> outing.stage = Outing.Stage.OUTBOUND;
				case OUTBOUND, HOMEWARD -> {
					if (isDay(world, at) && walk(traveler, outing, step)) {
						if (outing.stage == Outing.Stage.OUTBOUND) {
							outing.stage = Outing.Stage.THERE;
							outing.arrived = traveler.simulatedTo;
						} else {
							outing.stage = Outing.Stage.HOME;
						}
					}
				}
				case THERE -> {
					if (traveler.simulatedTo - outing.arrived >= outing.stay) {
						if (stayOutcome(world, register, traveler, random)) {
							return;
						}

						outing.stage = Outing.Stage.HOMEWARD;
					}
				}
				case HOME -> {
					traveler.simulatedTo = now;
				}
			}

			register.setDirty();
		}

		if (outing.stage == Outing.Stage.HOME && outing.passingThrough) {
			// A stranger has got where they were going, far off: out of these parts for good.
			register.remove(traveler);
			return;
		}

		if (traveler.settling > 0) {
			traveler.settling--;
		} else if (shouldMaterialize(world, traveler)) {
			materialize(world, register, traveler);
		}
	}

	/** Walks a step toward where they're headed; returns whether they got there. */
	private static boolean walk(TravelRegister.Traveler traveler, Outing outing, long ticks) {
		double dx = outing.targetX() + 0.5 - traveler.x;
		double dz = outing.targetZ() + 0.5 - traveler.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		double stride = PACE * ticks;
		if (distance <= stride + LongWalk.ARRIVED) {
			traveler.x = outing.targetX() + 0.5;
			traveler.z = outing.targetZ() + 0.5;
			return true;
		}

		traveler.x += dx / distance * stride;
		traveler.z += dz / distance * stride;
		return false;
	}

	/** What the stay came to, worked out by the kind of trip; returns true if they died there. */
	private static boolean stayOutcome(World world, TravelRegister register, TravelRegister.Traveler traveler, Random random) {
		OutingKind kind = OutingKinds.byId(traveler.outing.kind);
		EntityNPC npc = recreate(world, traveler);
		if (kind == null || npc == null) {
			return false;
		}

		OutingKind.Outcome outcome = kind.simulate(traveler.outing, npc, random);
		traveler.found.addAll(outcome.found());
		traveler.healthLost += outcome.healthLost();
		if (outcome.healthLost() > 0.0F) {
			traveler.outing.gotHurt = true;
		}

		if (!outcome.died()) {
			return false;
		}

		// Nobody saw it, but it happened: the death is posted as any other, for the family and the news.
		npc.moveTo(traveler.x, standingY(world, traveler), traveler.z, 0.0F, 0.0F);
		LifeEvents.post(new DeathEvent(npc, null));
		register.remove(traveler);
		return true;
	}

	/** Near a player, on ground they can stand on; or home, with the house loaded. */
	private static boolean shouldMaterialize(World world, TravelRegister.Traveler traveler) {
		TilePos pos = new TilePos((int) Math.floor(traveler.x), 0, (int) Math.floor(traveler.z));
		if (!world.isBlockLoaded(pos) || LongWalk.standingY(world, pos.x, pos.z) < 0) {
			return false;
		}

		if (NpcOuting.isKeepingReal()) {
			return true;
		}

		// Home counts from farther, but still only with a player near enough to keep the house loaded: a chunk with
		// nobody near may be unloaded any moment, and anyone put in it then would be lost with it.
		double reach = traveler.outing.stage == Outing.Stage.HOME ? HOME_DISTANCE : MATERIALIZE_DISTANCE;
		for (Player player : world.players) {
			double dx = player.x - traveler.x;
			double dz = player.z - traveler.z;
			if (dx * dx + dz * dz < reach * reach) {
				return true;
			}
		}

		return false;
	}

	/** Brings a traveler back into the world where their trip has got to, carrying what they found. */
	private static void materialize(World world, TravelRegister register, TravelRegister.Traveler traveler) {
		EntityNPC npc = recreate(world, traveler);
		if (npc == null) {
			return;
		}

		double y = traveler.outing.stage == Outing.Stage.HOME ? traveler.outing.homeY : standingY(world, traveler);
		double x = traveler.outing.stage == Outing.Stage.HOME ? traveler.outing.homeX + 0.5 : traveler.x;
		double z = traveler.outing.stage == Outing.Stage.HOME ? traveler.outing.homeZ + 0.5 : traveler.z;
		npc.moveTo(x, y, z, npc.yRot, 0.0F);
		npc.outing.start(traveler.outing);
		npc.outing.resetClock();
		for (ItemStack stack : traveler.found) {
			if (!npc.inventory.addToPack(stack)) {
				npc.dropItem(stack, 0.0F);
			}
		}

		if (traveler.healthLost > 0.0F) {
			npc.setHealthRaw(Math.max(1, Math.round(npc.getHealth() - traveler.healthLost)));
		}

		// The world turns away an entity whose chunk isn't loaded: then they stay a record, to try again.
		boolean joined = world.entityJoinedWorld(npc);
		if (joined) {
			register.remove(traveler);
		}
	}

	/** The traveler as an NPC again, from how they were saved; null if that fails. */
	@Nullable
	private static EntityNPC recreate(World world, TravelRegister.Traveler traveler) {
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		if (npc == null) {
			return null;
		}

		npc.load(traveler.npc);
		return npc;
	}

	private static double standingY(World world, TravelRegister.Traveler traveler) {
		int y = LongWalk.standingY(world, (int) Math.floor(traveler.x), (int) Math.floor(traveler.z));
		return y >= 0 ? y : world.getHeightValue((int) Math.floor(traveler.x), (int) Math.floor(traveler.z));
	}

	private static boolean isDay(World world, long time) {
		int hour = Calendar.hourOfDay(time);
		return hour >= DAY_FROM && hour < DAY_UNTIL;
	}
}
