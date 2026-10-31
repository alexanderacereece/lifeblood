package npclaude.client;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.player.Player;
import npclaude.dialogue.DialogueOption;
import npclaude.dialogue.DialogueSession;
import npclaude.dialogue.NpcDialogue;
import npclaude.entity.EntityNPC;
import org.jetbrains.annotations.NotNull;

/** Shows what the NPC says and the player's reply options as buttons. */
@Environment(EnvType.CLIENT)
public class NpcDialogueScreen extends Screen implements DialogueSession {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;
	private static final int NAME_COLOR = 0xFFFFE080;
	private static final int LINE_COLOR = 0xFFFFFFFF;

	private final EntityNPC npc;
	private final Player player;
	private NpcDialogue dialogue;
	private String npcLine;

	public NpcDialogueScreen(EntityNPC npc, Player player, NpcDialogue dialogue) {
		this.npc = npc;
		this.player = player;
		this.dialogue = dialogue;
		this.npcLine = dialogue.line();
	}

	@Override
	public void init() {
		this.npc.setTalkingTo(this.player);
		this.buttons.clear();
		List<DialogueOption> options = this.dialogue.options();
		int top = this.height / 2 - 10;
		for (int i = 0; i < options.size(); i++) {
			this.add(new ButtonElement(i, (this.width - BUTTON_WIDTH) / 2, top + i * BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT, options.get(i).text()));
		}
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		this.renderBackground();
		int centerX = this.width / 2;
		this.drawStringCenteredShadow(this.fontRenderer, this.npc.nickname, centerX, this.height / 2 - 60, NAME_COLOR);
		this.drawStringCenteredShadow(this.fontRenderer, "\"" + this.npcLine + "\"", centerX, this.height / 2 - 40, LINE_COLOR);
		super.render(mx, my, partialTick);
	}

	@Override
	protected void buttonClicked(@NotNull ButtonElement button) {
		List<DialogueOption> options = this.dialogue.options();
		if (button.id >= 0 && button.id < options.size()) {
			options.get(button.id).action().accept(this);
		}
	}

	@Override
	public void tick() {
		if (!this.npc.isAlive() || this.npc.getTalkingTo() != this.player) {
			this.close();
		}
	}

	@Override
	public void removed() {
		if (this.npc.getTalkingTo() == this.player) {
			this.npc.setTalkingTo(null);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// DialogueSession

	@Override
	public EntityNPC getNpc() {
		return this.npc;
	}

	@Override
	public Player getPlayer() {
		return this.player;
	}

	@Override
	public void say(String line) {
		this.npcLine = line;
	}

	@Override
	public void show(NpcDialogue dialogue) {
		this.dialogue = dialogue;
		this.npcLine = dialogue.line();
		this.init();
	}

	@Override
	public void openInventory() {
		this.mc.displayScreen(new NpcInventoryScreen(this.player, this.npc));
	}

	@Override
	public void close() {
		this.mc.displayScreen(null);
	}
}
