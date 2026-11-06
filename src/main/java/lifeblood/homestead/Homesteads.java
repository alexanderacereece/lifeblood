package lifeblood.homestead;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.chunk.Chunk;
import net.minecraft.core.world.pos.TilePos;
import lifeblood.Mod;
import lifeblood.building.LocalMaterials;
import lifeblood.building.Prefab;
import lifeblood.building.PrefabBeds;
import lifeblood.building.PrefabFiles;
import lifeblood.building.PrefabPlacer;
import lifeblood.building.Swaps;
import lifeblood.building.TerrainFit;
import lifeblood.entity.EntityNPC;
import lifeblood.homestead.HouseholdPlan.Member;
import lifeblood.ownership.BlockOwners;
import lifeblood.world.Calendar;
import org.jetbrains.annotations.Nullable;

/**
 * Generated homesteads: as the overworld decorates a new chunk, rarely, a house from the prefabs/homestead folder is
 * fitted to flat ground nearby, with its household already living there and its chests stocked. Everything stays
 * inside the area being decorated (16 by 16 blocks, offset half a chunk, like dungeons), whose chunks are all loaded,
 * so generating a homestead never loads or generates another chunk.
 */
public final class Homesteads {
	/** The decorated area starts half a chunk in from the chunk's corner and is a chunk across. */
	private static final int AREA_OFFSET = 8;
	private static final int AREA_SIZE = 16;

	private Homesteads() {
	}

	/** Called by the overworld decorator once a new chunk is decorated (see ChunkDecoratorOverworldMixin). */
	public static void decorate(World world, Chunk chunk) {
		int chunkX = chunk.pos.x;
		int chunkZ = chunk.pos.z;
		if (!HomesteadSites.isTried(world.getRandomSeed(), chunkX, chunkZ)) {
			return;
		}

		HomesteadRegister register = HomesteadRegister.get(world);
		if (register.cellTaken(HomesteadSites.cellOf(chunkX, chunkZ))) {
			return;
		}

		try {
			generate(world, register, chunkX, chunkZ, null, HomesteadSites.random(world.getRandomSeed(), chunkX, chunkZ));
		} catch (RuntimeException | IOException e) {
			// A broken prefab or an odd bit of land must never stop the world generating.
			Mod.LOG.warn("Could not generate a homestead at chunk {}, {}", chunkX, chunkZ, e);
		}
	}

	/**
	 * Puts a homestead in the area decorated with this chunk now, whatever the cell's rules, for testing: the named
	 * prefab, or a random homestead one if null. Says what happened: who lives there and where, or why not.
	 */
	public static String generateNow(World world, int chunkX, int chunkZ, @Nullable String prefabName) throws IOException {
		Random random = new Random();
		String result = generate(world, HomesteadRegister.get(world), chunkX, chunkZ, prefabName, random);
		return result != null ? result : "There's no flat, dry ground for it there (" + TerrainFit.MAX_UNEVENNESS + " blocks of rise at most).";
	}

	/** Generates a homestead in the area decorated with this chunk; returns a description of it, or null if there was no site. */
	@Nullable
	private static String generate(World world, HomesteadRegister register, int chunkX, int chunkZ, @Nullable String prefabName, Random random)
		throws IOException {
		List<Prefab> houses = homesteadPrefabs();
		if (prefabName != null) {
			houses.removeIf(house -> !house.name.equals(prefabName));
		}

		if (houses.isEmpty()) {
			return prefabName != null ? "There's no homestead prefab named " + prefabName + " with a bed in it." : "There are no homestead prefabs with beds.";
		}

		Prefab prefab = houses.get(random.nextInt(houses.size()));
		int minX = chunkX * 16 + AREA_OFFSET;
		int minZ = chunkZ * 16 + AREA_OFFSET;
		TerrainFit.Site site = null;
		int firstTurn = random.nextInt(4);
		for (int i = 0; i < 4 && site == null; i++) {
			site = TerrainFit.find(world, prefab, firstTurn + i, minX, minZ, minX + AREA_SIZE - 1, minZ + AREA_SIZE - 1);
		}

		if (site == null) {
			return null;
		}

		PrefabBeds.Layout beds = PrefabBeds.layout(prefab);
		HouseholdPlan plan = HouseholdPlanner.plan(beds.doubles().size(), beds.singles().size(), random);
		if (plan == null) {
			return "The " + prefab.name + " prefab has no beds.";
		}

		int[] size = PrefabPlacer.turnedSize(prefab, site.turns());
		Swaps swaps = LocalMaterials.survey(world, site.x() + size[0] / 2, site.y(), site.z() + size[1] / 2);
		PrefabPlacer.place(world, prefab, site.x(), site.y(), site.z(), site.turns(), swaps, null);
		TerrainFit.settle(world, prefab, site);

		double[] home = {site.x() + size[0] / 2.0, site.y() + 1.0, site.z() + size[1] / 2.0};
		List<EntityNPC> people = HomesteadPeople.settle(world, plan, bedSpots(prefab, site, beds, plan), home, random);
		if (people.isEmpty()) {
			return "Placed " + prefab.name + ", but its household couldn't be made.";
		}

		// The head of the household owns the house; their husband or wife and children share it (see Ownership).
		UUID head = people.get(0).getUuid();
		claim(world, prefab, site, head);
		HomesteadGoods.stock(world, prefab, site, swaps, people.size(), random);
		register.add(new HomesteadRegister.Homestead(site.x(), site.y(), site.z(), prefab.name, head,
			world.getWorldTime() - plan.yearsHere() * Calendar.ticksPerYear(world)));
		StringJoiner who = new StringJoiner(", ");
		for (EntityNPC npc : people) {
			who.add(npc.life.getFullName() + " (" + npc.life.getAgeYears() + ")");
		}

		String description = "a " + plan.kind().name().toLowerCase(Locale.ROOT) + " homestead (" + prefab.name + ") at " + site.x() + " " + site.y()
			+ " " + site.z() + ": " + who;
		Mod.LOG.info("Generated {}", description);
		return "Generated " + description + ".";
	}

	/** The homestead prefabs that have at least one bed, loaded; broken files are skipped. */
	private static List<Prefab> homesteadPrefabs() {
		List<Prefab> houses = new ArrayList<>();
		for (String name : PrefabFiles.tagged(PrefabFiles.HOMESTEAD_TAG)) {
			try {
				Prefab prefab = PrefabFiles.load(name);
				if (prefab != null && PrefabBeds.layout(prefab).sleeps() > 0 && Math.max(prefab.width, prefab.depth) <= AREA_SIZE) {
					houses.add(prefab);
				}
			} catch (IOException e) {
				Mod.LOG.warn("Could not read the homestead prefab {}", name, e);
			}
		}

		return houses;
	}

	/** Where each member stands to start with: on their own bed, the couple's side by side. */
	private static List<double[]> bedSpots(Prefab prefab, TerrainFit.Site site, PrefabBeds.Layout beds, HouseholdPlan plan) {
		List<double[]> spots = new ArrayList<>();
		int[] usedSide = new int[beds.doubles().size()];
		for (Member member : plan.members()) {
			PrefabBeds.Bed bed = member.inDouble() ? beds.doubles().get(member.bed())[usedSide[member.bed()]++ % 2] : bedOrFirst(beds, member.bed());
			spots.add(spot(prefab, site, bed));
		}

		return spots;
	}

	/** A single bed by its index; if there are fewer, the first bed there is. */
	private static PrefabBeds.Bed bedOrFirst(PrefabBeds.Layout beds, int index) {
		if (index < beds.singles().size()) {
			return beds.singles().get(index);
		}

		return beds.doubles().isEmpty() ? beds.singles().get(0) : beds.doubles().get(0)[0];
	}

	/** Standing on top of a bed, in world coordinates. */
	private static double[] spot(Prefab prefab, TerrainFit.Site site, PrefabBeds.Bed bed) {
		TilePos pos = PrefabPlacer.spotPosition(prefab, bed.x(), bed.y(), bed.z(), site.x(), site.y(), site.z(), site.turns(), new TilePos());
		return new double[] {pos.x + 0.5, pos.y + 0.6, pos.z + 0.5};
	}

	/** Records every block the house placed as the head's. */
	private static void claim(World world, Prefab prefab, TerrainFit.Site site, UUID owner) {
		TilePos pos = new TilePos();
		for (int y = 0; y < prefab.height; y++) {
			for (int z = 0; z < prefab.depth; z++) {
				for (int x = 0; x < prefab.width; x++) {
					if (!"minecraft:block/air".equals(prefab.blockAt(x, y, z))) {
						PrefabPlacer.spotPosition(prefab, x, y, z, site.x(), site.y(), site.z(), site.turns(), pos);
						BlockOwners.setOwner(world, pos.x, pos.y, pos.z, owner);
					}
				}
			}
		}
	}
}
