package lifeblood.appearance;

import com.mojang.nbt.tags.CompoundTag;
import lifeblood.entity.EntityNPC;
import lifeblood.entity.NpcGender;
import lifeblood.entity.NpcPart;

/**
 * How one NPC looks: a skin file from npc_skins, or else a look built from appearance parts, and the arm width it's
 * drawn with. Kept in the entity's synced data (ids in EntityNPC) so the client can draw it; the client turns it into
 * a texture (client/NpcSkins, client/AppearanceTextures).
 */
public final class NpcLooks implements NpcPart {
	private final EntityNPC npc;

	public NpcLooks(EntityNPC npc) {
		this.npc = npc;
	}

	/** The skin file's name in npc_skins, without .png; empty when it's built from parts instead. */
	public String getSkin() {
		return this.npc.getEntityData().getString(EntityNPC.DATA_SKIN);
	}

	public void setSkin(String skin) {
		this.npc.getEntityData().set(EntityNPC.DATA_SKIN, skin);
	}

	public SkinModel getSkinModel() {
		return SkinModel.byId(this.npc.getEntityData().getByte(EntityNPC.DATA_SKIN_MODEL));
	}

	public void setSkinModel(SkinModel model) {
		this.npc.getEntityData().set(EntityNPC.DATA_SKIN_MODEL, (byte) model.ordinal());
	}

	/** The arm width its look from parts is drawn with: the skin model, except that women are slim unless set otherwise. */
	public SkinModel getLookModel() {
		SkinModel model = this.getSkinModel();
		return model == SkinModel.AUTO && this.npc.getGender() == NpcGender.FEMALE ? SkinModel.SLIM : model;
	}

	/** The look built from appearance parts, encoded (see {@link Appearance}); shown when the NPC has no skin file set. */
	public String getAppearance() {
		return this.npc.getEntityData().getString(EntityNPC.DATA_APPEARANCE);
	}

	public void setAppearance(Appearance appearance) {
		this.setEncodedAppearance(appearance.encode());
	}

	private void setEncodedAppearance(String encoded) {
		this.npc.getEntityData().set(EntityNPC.DATA_APPEARANCE, encoded);
	}

	/**
	 * Gives an NPC with no skin file and no look yet a random one from the parts available (if there are any),
	 * suited to where it's from. Runs once after spawning or loading, so NPCs get a look once parts are added.
	 */
	public void rollIfNeeded() {
		this.npc.life.ensureHomeland();
		if (this.getSkin().isEmpty() && this.getAppearance().isEmpty()) {
			this.randomize();
		}
	}

	/** A new random look from the parts available, suited to where it's from and grayed for its age. */
	public void randomize() {
		float warmth = this.npc.life.getHomeWarmth();
		this.setAppearance(Appearance.random(PartCatalog.get(), Wearer.of(this.npc.getGender(), warmth), this.npc.getRandom(), warmth));
		this.ageHair(this.npc.life.getAgeYears());
	}

	/** Grays its hair as far as it has at this age (see HairColor): a little more every few years from middle age. */
	public void ageHair(int age) {
		Appearance look = Appearance.decode(this.getAppearance());
		HairColor hair = look.hairColor();
		if (hair == null) {
			return;
		}

		int gray = HairColor.grayAt(age, HairColor.onsetAge(this.npc.getUuid()));
		if (gray != hair.gray()) {
			this.setAppearance(look.withHairColor(hair.withGray(gray).encode()));
		}
	}

	@Override
	public String saveKey() {
		return "Looks";
	}

	@Override
	public void save(CompoundTag tag) {
		tag.putString("Skin", this.getSkin());
		tag.putString("Appearance", this.getAppearance());
		tag.putByte("SkinModel", (byte) this.getSkinModel().ordinal());
	}

	@Override
	public void load(CompoundTag tag) {
		this.setSkin(tag.getStringOrDefault("Skin", ""));
		this.setEncodedAppearance(tag.getStringOrDefault("Appearance", ""));
		this.setSkinModel(SkinModel.byId(tag.getByte("SkinModel")));
	}
}
