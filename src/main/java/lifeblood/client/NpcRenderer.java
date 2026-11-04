package lifeblood.client;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.MobRendererBipedArmored;
import net.minecraft.client.render.renderer.BlendFactor;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.MathHelper;
import lifeblood.combat.FactionRelation;
import lifeblood.combat.NpcFactions;
import lifeblood.entity.EntityNPC;
import lifeblood.util.Geometry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Vector3dc;
import org.useless.dragonfly.data.entity.mojang.EntityGeometryMojangData.Cache;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.useless.dragonfly.models.entity.BoneTransform;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/**
 * Draws NPCs with the full player model (hat, jacket, sleeves and pant layers included), plus worn armor
 * and the held item, borrowing the player's model mappings and the human mob's rendering transform.
 */
@Environment(EnvType.CLIENT)
public class NpcRenderer extends MobRendererBipedArmored<EntityNPC> {
	private static final NamespaceID PLAYER_MODELS = NamespaceID.fromPool("minecraft", "player");
	private static final float PLAYER_SCALE = 0.9375F;
	/** A newborn's head, as a share of an adult's. */
	private static final float CHILD_HEAD_START = 0.75F;
	/** Where the head turns and scales from (the neck), in model pixels, per the player model. */
	private static final float HEAD_PIVOT_Y = 24.0F;
	private static final float LYING_LIFT = 0.125F;
	private static final double HEALTH_BAR_RANGE = 16.0;
	/** Bar size in name-tag pixels; the name tag sits 0.8 above the head, the bar just above it. */
	private static final float HEALTH_BAR_WIDTH = 40.0F;
	private static final float HEALTH_BAR_THICKNESS = 3.0F;
	private static final float HEALTH_BAR_HEIGHT_ABOVE_HEAD = 1.15F;

	private final Map<String, StaticEntityModel> models = new HashMap<>();

	public NpcRenderer() {
		super(0.5F);
	}

	@Override
	protected void preRenderTransform(@NotNull EntityNPC entity, double x, double y, double z, float yaw, float partialTick) {
		// Lying down, the body is lifted by half its thickness so it rests on the ground instead of in it.
		float fall = entity.health.getFallAmount(partialTick);
		GLRenderer.modelM4f().translate((float) x, (float) y + fall * LYING_LIFT, (float) z);
		GLRenderer.modelM4f().rotateY(-this.getBodyYaw(entity, partialTick));
		GLRenderer.modelM4f().scale(0.062539086F, 0.062539086F, -0.062539086F);
		GLRenderer.modelM4f().scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
		// Children are drawn smaller, growing to full size as they grow up.
		float size = entity.life.getSizeScale();
		GLRenderer.modelM4f().scale(size, size, size);
		if (fall > 0.0F) {
			GLRenderer.modelM4f().rotateX(Math.toRadians((entity.health.isDownedOnFront() ? -90.0F : 90.0F) * fall));
		} else if (entity.deathTime > 0) {
			float rotationProgress = (entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
			rotationProgress = Math.min(MathHelper.sqrt_float(rotationProgress), 1.0F);
			GLRenderer.modelM4f().rotateZ(Math.toRadians(rotationProgress * this.getMaxDeathRotation(entity)));
		}
	}

	/**
	 * Children's heads are bigger for their size: the body grows from half an adult's size to full, while the head
	 * grows from three quarters to full over the same years, so little ones have little-one proportions.
	 */
	@Nullable
	@Override
	protected StaticEntityModel setupAnimations(@NotNull EntityNPC entity, @Nullable StaticEntityModel model, float partialTick, int layer) {
		StaticEntityModel posed = super.setupAnimations(entity, model, partialTick, layer);
		if (posed != null && entity.life.isChild()) {
			float growth = entity.life.getGrowth();
			float head = CHILD_HEAD_START + (1.0F - CHILD_HEAD_START) * growth;
			float relative = head / entity.life.getSizeScale();
			BoneTransform transform = posed.getTransform("head");
			transform.scaleX *= relative;
			transform.scaleY *= relative;
			transform.scaleZ *= relative;
			// Bones scale around the model's origin (at the feet), so a bigger head would also float upward. Moving it
			// back by how far the neck (the head's pivot) moved, turned the way the head is turned, keeps it on the neck.
			// (The same turn the model applies: radians, with X and Z negated.)
			Vector3f offset = new Matrix4f()
				.rotateZ((float) -transform.rotZ)
				.rotateY((float) transform.rotY)
				.rotateX((float) -transform.rotX)
				.transformDirection(new Vector3f(0.0F, HEAD_PIVOT_Y * (1.0F - relative), 0.0F));
			transform.posX += offset.x;
			transform.posY += offset.y;
			transform.posZ += offset.z;
		}

		return posed;
	}

	@Override
	public void loadEntityTexture(@NotNull EntityNPC entity) {
		NpcSkins.LoadedSkin skin = this.getSkin(entity);
		if (skin != null) {
			this.renderDispatcher.textureManager.bindTexture(skin.texture());
		} else {
			this.bindTexture(NpcSkins.DEFAULT_TEXTURE);
		}
	}

	@Nullable
	@Override
	protected StaticEntityModel getActiveModel(@NotNull EntityNPC entity) {
		NpcSkins.LoadedSkin skin = this.getSkin(entity);
		return this.getModel(skin != null && skin.slim() ? "main.slim" : "main");
	}

	@NotNull
	@Override
	protected StaticEntityModel getModel(@NotNull String localId) {
		return this.models.computeIfAbsent(localId, id -> Cache.getModelForEntity(PLAYER_MODELS, id));
	}

	/** Set while drawing an NPC as a paper doll in a screen, which shows no name tag or health bar. */
	public static boolean drawingPreview;

	/** Draws the name tag as usual, then a health bar above it if the viewer is looking at this NPC and it isn't hostile to them. */
	@Override
	protected void renderSpecials(@NotNull TessellatorGeneral tessellator, @NotNull EntityNPC entity, double x, double y, double z) {
		if (drawingPreview) {
			return;
		}

		super.renderSpecials(tessellator, entity, x, y, z);
		Player viewer = Minecraft.getMinecraft().thePlayer;
		if (viewer != null && !entity.removed && entity.getHealth() > 0 && NpcFactions.regard(entity, viewer) != FactionRelation.HOSTILE && isLookingAt(viewer, entity)) {
			this.renderHealthBar(tessellator, entity, x, y, z);
		}
	}

	/** Traces the viewer's line of sight (up to 16 blocks) against the NPC's body. */
	private static boolean isLookingAt(Player viewer, EntityNPC entity) {
		Vector3dc eye = viewer.getPosition(1.0F, true);
		Vector3dc look = viewer.getViewVector(1.0F);
		return Geometry.segmentHitsBox(eye.x(), eye.y(), eye.z(), eye.x() + look.x() * HEALTH_BAR_RANGE, eye.y() + look.y() * HEALTH_BAR_RANGE,
			eye.z() + look.z() * HEALTH_BAR_RANGE, entity.bb, 0.1);
	}

	/** A bar above the name tag, green at full health through yellow to red, drawn facing the camera like the name. */
	private void renderHealthBar(TessellatorGeneral t, EntityNPC entity, double x, double y, double z) {
		float fraction = Math.max(0.0F, Math.min(1.0F, entity.getHealth() / (float) entity.getMaxHealth()));
		float red = fraction < 0.5F ? 1.0F : 2.0F * (1.0F - fraction);
		float green = fraction > 0.5F ? 1.0F : 2.0F * fraction;
		float half = HEALTH_BAR_WIDTH / 2.0F;
		float filled = -half + HEALTH_BAR_WIDTH * fraction;

		GLRenderer.pushFrame();
		GLRenderer.modelM4f().translate((float) x, (float) y + entity.getHeadHeight() + HEALTH_BAR_HEIGHT_ABOVE_HEAD, (float) z);
		GLRenderer.modelM4f().rotateY(Math.toRadians(-this.renderDispatcher.viewLerpYaw));
		GLRenderer.modelM4f().rotateX(Math.toRadians(this.renderDispatcher.viewLerpPitch));
		GLRenderer.modelM4f().scale(-0.026666667F, -0.026666667F, 0.026666667F);
		GLRenderer.globalSetLightEnabled(false);
		GLRenderer.setDepthMask(false);
		GLRenderer.enableState(State.BLEND);
		GLRenderer.setBlendFunc(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
		GLRenderer.setShader(Shaders.COLOR);
		t.startDrawingQuads();
		t.setColor4f(0.0F, 0.0F, 0.0F, 0.5F);
		quad(t, -half - 1.0F, -1.0F, half + 1.0F, HEALTH_BAR_THICKNESS + 1.0F);
		t.setColor4f(red, green, 0.1F, 0.9F);
		quad(t, -half, 0.0F, filled, HEALTH_BAR_THICKNESS);
		t.draw();
		GLRenderer.setDepthMask(true);
		GLRenderer.globalSetLightEnabled(true);
		GLRenderer.disableState(State.BLEND);
		GLRenderer.popFrame();
	}

	private static void quad(TessellatorGeneral t, float x0, float y0, float x1, float y1) {
		t.addVertex(x0, y0, 0.0);
		t.addVertex(x0, y1, 0.0);
		t.addVertex(x1, y1, 0.0);
		t.addVertex(x1, y0, 0.0);
	}

	@Nullable
	private NpcSkins.LoadedSkin getSkin(EntityNPC entity) {
		// A skin file, if one is set, wins; otherwise the look built from appearance parts.
		if (!entity.getSkin().isEmpty()) {
			return NpcSkins.get(this.renderDispatcher.textureManager, entity.getSkin(), entity.getSkinModel());
		}

		return AppearanceTextures.get(this.renderDispatcher.textureManager, entity.getAppearance(), entity.getLookModel());
	}
}
