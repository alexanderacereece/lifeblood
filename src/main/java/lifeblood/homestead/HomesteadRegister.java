package lifeblood.homestead;

import com.mojang.nbt.tags.CompoundTag;
import com.mojang.nbt.tags.ListTag;
import com.mojang.nbt.tags.Tag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.world.World;
import net.minecraft.core.world.saveddata.SavedData;

/**
 * Every homestead the world has generated, saved with it (BTA SavedData): where it is, which prefab, who heads the
 * household, and when they settled. Also how a cell knows it already has its homestead.
 */
public class HomesteadRegister extends SavedData {
	public static final String DATA_ID = "lifeblood_homesteads";

	/** One homestead: the house's box corner and floor level, its prefab, the head of the household, and when they settled (world time). */
	public record Homestead(int x, int y, int z, String prefab, UUID head, long settled) {
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
						homestead.getString("Prefab"), UUID.fromString(homestead.getString("Head")), homestead.getLong("Settled")));
				} catch (IllegalArgumentException e) {
					// A damaged entry is skipped rather than losing the rest.
				}
			}
		}
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
			list.addTag(entry);
		}

		tag.putList("Homesteads", list);
	}
}
