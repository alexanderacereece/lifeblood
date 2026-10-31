package npclaude.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.container.ScreenContainerAbstract;
import net.minecraft.client.render.EntityRendererDispatcher;
import net.minecraft.client.render.Lighting;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.client.render.renderer.Shaders;
import net.minecraft.client.render.renderer.State;
import net.minecraft.core.entity.player.Player;
import npclaude.entity.EntityNPC;
import npclaude.inventory.NpcMenu;
import org.joml.Math;

/**
 * The NPC's inventory, styled after the player's own inventory screen:
 * armor column and a live preview of the NPC on the left, hand slot and pack on the right.
 */
@Environment(EnvType.CLIENT)
public class NpcInventoryScreen extends ScreenContainerAbstract {
	private static final String TEXTURE = "/assets/npclaude/textures/gui/npc_inventory.png";
	private static final int LABEL_COLOR = 4210752;

	private final Player player;
	private final EntityNPC npc;
	private float mouseX;
	private float mouseY;

	public NpcInventoryScreen(Player player, EntityNPC npc) {
		super(new NpcMenu(player.inventory, npc));
		this.player = player;
		this.npc = npc;
	}

	@Override
	public void init() {
		super.init();
		this.npc.setTalkingTo(this.player);
	}

	@Override
	public void removed() {
		super.removed();
		if (this.npc.getTalkingTo() == this.player) {
			this.npc.setTalkingTo(null);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.inventorySlots.stillValid(this.player)) {
			this.mc.displayScreen(null);
		}
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		this.mouseX = mx;
		this.mouseY = my;
		super.render(mx, my, partialTick);
	}

	@Override
	protected void drawGuiContainerForegroundLayer() {
		this.drawStringNoShadow(this.fontRenderer, this.npc.nickname, 86, 6, LABEL_COLOR);
		this.drawStringNoShadow(this.fontRenderer, "Pack", 116, 16, LABEL_COLOR);
		this.drawStringNoShadow(this.fontRenderer, "Hand", 86, 52, LABEL_COLOR);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTick) {
		GLRenderer.setColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		this.mc.textureManager.loadTexture(TEXTURE).bind();
		int x = (this.width - this.xSize) / 2;
		int y = (this.height - this.ySize) / 2;
		this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);
		this.drawNpcPreview(x + 51, y + 75, partialTick);
	}

	/** Same preview as the player inventory: the model turns to follow the mouse. */
	private void drawNpcPreview(int x, int y, float partialTick) {
		EntityNPC npc = this.npc;
		float yBodyRot = npc.yBodyRot;
		float yBodyRotO = npc.yBodyRotO;
		float yRot = npc.yRot;
		float yRotO = npc.yRotO;
		float xRot = npc.xRot;
		float xRotO = npc.xRotO;
		float eyesX = x - this.mouseX;
		float eyesY = y - 50 - this.mouseY;

		GLRenderer.pushFrame();
		GLRenderer.setShader(Shaders.WORLD);
		GLRenderer.getFogState().disable();
		GLRenderer.enableState(State.DEPTH_TEST);
		GLRenderer.modelM4f().translate(x, y, 50.0F);
		GLRenderer.modelM4f().scale(-30.0F, 30.0F, 30.0F);
		GLRenderer.modelM4f().rotateZ(Math.toRadians(180.0F));
		GLRenderer.modelM4f().rotateY(Math.toRadians(135.0F));
		Lighting.enableLight();
		GLRenderer.setLightMapStrength(0.0F);
		GLRenderer.modelM4f().rotateY(Math.toRadians(-135.0F));
		GLRenderer.modelM4f().rotateX(Math.toRadians(-((float) java.lang.Math.atan(eyesY / 40.0F)) * 20.0F));
		npc.yBodyRotO = npc.yBodyRot = (float) java.lang.Math.atan(eyesX / 40.0F) * 20.0F;
		npc.yRotO = npc.yRot = (float) java.lang.Math.atan(eyesX / 40.0F) * 40.0F;
		npc.xRotO = npc.xRot = -((float) java.lang.Math.atan(eyesY / 40.0F)) * 20.0F;
		EntityRendererDispatcher.instance.viewLerpYaw = 180.0F;
		EntityRenderer.renderShadows = false;
		GLRenderer.globalGetNormalTransformMatrix().scale(1.0F, -1.0F, 1.0F);
		GLRenderer.setLightmapCoord2i(15, 15);
		EntityRendererDispatcher.instance.renderEntityPreviewWithPosYaw(GLRenderer.getTessellator(), npc, 0.0, 0.0, 0.0, 0.0F, partialTick);
		EntityRenderer.renderShadows = true;
		npc.yBodyRot = yBodyRot;
		npc.yBodyRotO = yBodyRotO;
		npc.yRot = yRot;
		npc.yRotO = yRotO;
		npc.xRot = xRot;
		npc.xRotO = xRotO;
		GLRenderer.popFrame();
		Lighting.disable();
	}
}
