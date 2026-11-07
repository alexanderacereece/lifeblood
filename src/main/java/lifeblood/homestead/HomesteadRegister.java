package lifeblood.homestead;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Every homestead the world has generated, saved with it (BTA SavedData): where it is, which prefab, who heads the
 * household, and when they settled. Also how a cell knows it already has its homestead.
 */
public class HomesteadRegister extends SavedData {
	public static final String DATA_ID = "lifeblood_homesteads";

	/**
	 * One homestead: the house's box (lowest corner, at floor level, and its size once turned), its prefab, the head
	 * of the household, when they settled (world time), and where its chests, furnaces and crafting tables are
	 * ({x, y, z} each).
	 */
	public record Homestead(int x, int y, int z, int width, int height, int depth, String prefab, UUID head, long settled, List<int[]> chests,
		List<int[]> furnaces, List<int[]> workbenches) {
		/** Whether a position is inside the house's box. */
		public boolean contains(double px, double py, double pz) {
			return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height && pz >= this.z && pz < this.z + this.depth;
		}
	}

	private final List<Homestead> homesteads = new ArrayList<>();

	/** Called by BTA through reflection when loading saved data. */
	public HomesteadRegister(String id) {
		super(id);
	}

	public static HomesteadRegister get(World world) {
		HomesteadRegister register = (HomesteadRegister) world.getSavedData(HomesteadRegister.class, DATA_ID);
		if (register == null) {
			register = new HomesteadRegister(DATA_ID);
			world.setSavedData(DATA_ID, register);
			register.setDirty();
		}

		return register;
	}

	public List<Homestead> all() {
		return Collections.unmodifiableList(this.homesteads);
	}

	/** Whether a homestead already stands in this cell (see HomesteadSites.cellOf). */
	public boolean cellTaken(long cell) {
		for (Homestead homestead : this.homesteads) {
			if (HomesteadSites.cellOf(homestead.x() >> 4, homestead.z() >> 4) == cell) {
				return true;
			}
		}

		return false;
	}

	/** The homestead whose house holds this position (someone's home), or null if none does. */
	@Nullable
	public Homestead at(double x, double y, double z) {
		for (Homestead homestead : this.homesteads) {
			if (homestead.contains(x, y, z)) {
				return homestead;
			}
		}

		return null;
	}

	public void add(Homestead homestead) {
		this.homesteads.add(homestead);
		this.setDirty();
	}

	@Override
	public void load(CompoundTag tag) {
		this.homesteads.clear();
		for (Tag<?> entry : tag.getList("Homesteads")) {
			if (entry instanceof CompoundTag homestead) {
				try {
					this.homesteads.add(new Homestead(homestead.getInteger("X"), homestead.getInteger("Y"), homestead.getInteger("Z"),
						homestead.getInteger("Width"), homestead.getInteger("Height"), homestead.getInteger("Depth"), homestead.getString("Prefab"),
						UUID.fromString(homestead.getString("Head")), homestead.getLong("Settled"), readPositions(homestead.getStringOrDefault("Chests", "")),
						readPositions(homestead.getStringOrDefault("Furnaces", "")), readPositions(homestead.getStringOrDefault("Workbenches", ""))));
				} catch (IllegalArgumentException e) {
					// A damaged entry is skipped rather than losing the rest.
				}
			}
		}
	}

	/** Positions saved as "x,y,z;x,y,z". */
	private static List<int[]> readPositions(String saved) {
		List<int[]> positions = new ArrayList<>();
		for (String part : saved.split(";")) {
			String[] numbers = part.split(",");
			if (numbers.length == 3) {
				positions.add(new int[] {Integer.parseInt(numbers[0].trim()), Integer.parseInt(numbers[1].trim()), Integer.parseInt(numbers[2].trim())});
			}
		}

		return positions;
	}

	private static String writePositions(List<int[]> positions) {
		StringJoiner joiner = new StringJoiner(";");
		for (int[] pos : positions) {
			joiner.add(pos[0] + "," + pos[1] + "," + pos[2]);
		}

		return joiner.toString();
	}

	@Override
	public void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Homestead homestead : this.homesteads) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("X", homestead.x());
			entry.putInt("Y", homestead.y());
			entry.putInt("Z", homestead.z());
			entry.putString("Prefab", homestead.prefab());
			entry.putString("Head", homestead.head().toString());
			entry.putLong("Settled", homestead.settled());
			entry.putInt("Width", homestead.width());
			entry.putInt("Height", homestead.height());
			entry.putInt("Depth", homestead.depth());
			entry.putString("Chests", writePositions(homestead.chests()));
			entry.putString("Furnaces", writePositions(homestead.furnaces()));
			entry.putString("Workbenches", writePositions(homestead.workbenches()));
			list.addTag(entry);
		}

		tag.putList("Homesteads", list);
	}
}
