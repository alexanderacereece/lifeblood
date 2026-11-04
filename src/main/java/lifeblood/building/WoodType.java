package lifeblood.building;

import java.util.Locale;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * The kinds of wood that grow in different places. In BTA every log gives the same oak planks, so the logs are what
 * change: a prefab's logs become the local kind wherever it's placed. Mossy and scorched logs aren't kinds of tree, so
 * they stay as built.
 */
public enum WoodType {
	OAK(Blocks.LOG_OAK),
	PINE(Blocks.LOG_PINE),
	BIRCH(Blocks.LOG_BIRCH),
	CHERRY(Blocks.LOG_CHERRY),
	EUCALYPTUS(Blocks.LOG_EUCALYPTUS),
	THORN(Blocks.LOG_THORN),
	PALM(Blocks.LOG_PALM);

	public final Block<?> log;

	WoodType(Block<?> log) {
		this.log = log;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Nullable
	public static WoodType byId(String id) {
		for (WoodType type : values()) {
			if (type.id().equals(id)) {
				return type;
			}
		}

		return null;
	}

	/** The wood a log is, or null if the block isn't one of these logs. */
	@Nullable
	public static WoodType of(Block<?> block) {
		for (WoodType type : values()) {
			if (type.log == block) {
				return type;
			}
		}

		return null;
	}

	/** This wood's log in place of another kind's; anything else stays as it is. */
	public Block<?> swap(Block<?> block) {
		return of(block) != null ? this.log : block;
	}
}
