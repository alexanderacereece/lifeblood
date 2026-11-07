package lifeblood.travel;

import java.util.Random;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.world.World;
import lifeblood.NpcConfig;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.family.Romance;
import lifeblood.people.NamePool;
import lifeblood.world.WorldSystem;

/**
 * Strangers passing through (a WorldSystem): now and then, somewhere out beyond where a player is, a person sets out for
 * home far off on the other side of them, so their road passes near. They're real people from the start, in the register
 * with a name, an age and what they carry, walking out of sight (TravelRegister) until their road brings them near a
 * player, and on out of these parts after.
 */
public final class Strangers {
	/** How often a stranger may set out, in ticks (a minute). */
	private static final int INTERVAL = 1200;
	/** How far from the player they start, and how far past the player their home is, in blocks. */
	private static final int START_DISTANCE = 220;
	private static final int HOME_DISTANCE = 1500;
	/** How far to one side of the player their road may pass, in blocks. */
	private static final int PASS_WIDE = 40;

	public static final WorldSystem SYSTEM = WorldSystem.every(INTERVAL, Strangers::tick);

	private Strangers() {
	}

	private static void tick(World world) {
		if (world.isClientSide || world.dimension.id != 0) {
			return;
		}

		double chance = NpcConfig.strangersPerDay() * INTERVAL / 24000.0;
		for (Player player : world.players) {
			if (world.rand.nextDouble() < chance) {
				setOut(world, player, world.rand);
			}
		}
	}

	/** A stranger sets out from beyond a player, for a home on the far side of them. Public for testing by command. */
	public static EntityNPC setOut(World world, Player player, Random random) {
		EntityNPC npc = EntityDispatcher.getInstance().createEntityInWorld(EntityNPC.class, world);
		double angle = random.nextDouble() * Math.PI * 2.0;
		double side = (random.nextDouble() - 0.5) * 2.0 * PASS_WIDE;
		double startX = player.x + Math.cos(angle) * START_DISTANCE - Math.sin(angle) * side;
		double startZ = player.z + Math.sin(angle) * START_DISTANCE + Math.cos(angle) * side;
		int homeX = (int) Math.floor(player.x - Math.cos(angle) * HOME_DISTANCE);
		int homeZ = (int) Math.floor(player.z - Math.sin(angle) * HOME_DISTANCE);

		NpcGender gender = random.nextBoolean() ? NpcGender.FEMALE : NpcGender.MALE;
		npc.moveTo(startX, player.y, startZ, 0.0F, 0.0F);
		npc.setGender(gender);
		npc.setNickname(NamePool.pick(gender, random));
		npc.life.setBirthFamilyName(NamePool.pickFamilyName(random));
		npc.life.setAge(19 + random.nextInt(42), random.nextFloat());
		npc.home.set(homeX, player.y, homeZ);
		packFor(npc, random);

		// Headed home: where they're going is where they live.
		Outing outing = new Outing("journey");
		outing.passingThrough = true;
		outing.stage = Outing.Stage.OUTBOUND;
		outing.started = world.getWorldTime();
		outing.destX = homeX;
		outing.destZ = homeZ;
		outing.homeX = homeX;
		outing.homeY = (int) player.y;
		outing.homeZ = homeZ;
		npc.outing.start(outing);
		Romance.register(npc);
		TravelRegister.get(world).add(TravelRegister.recordFor(npc, outing, startX, startZ, world.getWorldTime()));
		return npc;
	}

	/** What a traveler on a long road carries: food, torches, and often something to defend themself with. */
	private static void packFor(EntityNPC npc, Random random) {
		npc.inventory.addToPack(new ItemStack(Items.FOOD_BREAD, 3 + random.nextInt(5)));
		if (random.nextBoolean()) {
			npc.inventory.addToPack(new ItemStack(Items.FOOD_APPLE, 1 + random.nextInt(4)));
		}

		npc.inventory.addToPack(new ItemStack(Blocks.TORCH_COAL, 2 + random.nextInt(8)));
		float roll = random.nextFloat();
		if (roll < 0.4F) {
			npc.inventory.addToPack(new ItemStack(random.nextBoolean() ? Items.TOOL_SWORD_STONE : Items.TOOL_SWORD_IRON, 1));
		} else if (roll < 0.6F) {
			npc.inventory.addToPack(new ItemStack(Items.TOOL_BOW, 1));
			npc.inventory.addToPack(new ItemStack(Items.AMMO_ARROW, 8 + random.nextInt(16)));
		}

		if (random.nextFloat() < 0.3F) {
			npc.inventory.addToPack(new ItemStack(Items.TOOL_PICKAXE_STONE, 1));
		}
	}
}
