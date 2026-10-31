package npclaude.client;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.MobRendererBiped;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.MathHelper;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.useless.dragonfly.data.entity.mojang.EntityGeometryMojangData.Cache;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/**
 * Draws NPCs with the full player model (hat, jacket, sleeves and pant layers included),
 * borrowing BTA's human mob model mappings and its rendering transform.
 */
@Environment(EnvType.CLIENT)
public class NpcRenderer extends MobRendererBiped<EntityNPC> {
	private static final NamespaceID HUMAN_MODELS = NamespaceID.getPermanent("minecraft", "human");
	private static final float PLAYER_SCALE = 0.9375F;

	private final Map<String, StaticEntityModel> models = new HashMap<>();

	public NpcRenderer() {
		super(0.5F);
	}

	@Override
	protected void preRenderTransform(@NotNull EntityNPC entity, double x, double y, double z, float yaw, float partialTick) {
		GLRenderer.modelM4f().translate((float) x, (float) y, (float) z);
		GLRenderer.modelM4f().rotateY(-this.getBodyYaw(entity, partialTick));
		GLRenderer.modelM4f().scale(0.062539086F, 0.062539086F, -0.062539086F);
		GLRenderer.modelM4f().scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
		if (entity.deathTime > 0) {
			float rotationProgress = (entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
			rotationProgress = Math.min(MathHelper.sqrt_float(rotationProgress), 1.0F);
			GLRenderer.modelM4f().rotateZ(Math.toRadians(rotationProgress * this.getMaxDeathRotation(entity)));
		}
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
		return this.models.computeIfAbsent(localId, id -> Cache.getModelForEntity(HUMAN_MODELS, id));
	}

	@Nullable
	private NpcSkins.LoadedSkin getSkin(EntityNPC entity) {
		return NpcSkins.get(this.renderDispatcher.textureManager, entity.getSkin(), entity.getSkinModel());
	}
}
