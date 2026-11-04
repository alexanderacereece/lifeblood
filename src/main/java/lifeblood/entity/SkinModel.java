package lifeblood.entity;

import org.jetbrains.annotations.Nullable;

/**
 * Which arm width an NPC's skin is drawn with.
 * AUTO reads it from the skin file (slim skins leave the outer arm column transparent).
 */
public enum SkinModel {
	AUTO,
	WIDE,
	SLIM;

	public static SkinModel byId(int id) {
		SkinModel[] values = values();
		return id >= 0 && id < values.length ? values[id] : AUTO;
	}

	@Nullable
	public static SkinModel byName(String name) {
		for (SkinModel model : values()) {
			if (model.name().equalsIgnoreCase(name)) {
				return model;
			}
		}
		return null;
	}
}
