package npclaude.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.sound.SoundCategory;
import npclaude.dialogue.DialogueOption;
import npclaude.dialogue.DialogueSession;
import npclaude.dialogue.NpcDialogue;
import npclaude.entity.EntityNPC;

/**
 * RPG-style conversation: the NPC's line in a box along the bottom of the screen,
 * the player's replies listed on the right. The world stays visible behind it.
 * Replies can be clicked or picked with the number keys.
 */
@Environment(EnvType.CLIENT)
public class NpcDialogueScreen extends Screen implements DialogueSession {
	private static final int MARGIN = 8;
	private static final int PADDING = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int BOX_MAX_WIDTH = 360;
	private static final int BOX_MIN_LINES = 3;
	private static final int CHOICE_HEIGHT = 16;
	private static final int CHOICE_GAP = 3;
	private static final int CHOICE_MIN_WIDTH = 120;

	private static final int BOX_COLOR = 0xC0101018;
	private static final int BORDER_COLOR = 0xFF8B7B55;
	private static final int CHOICE_COLOR = 0x90101018;
	private static final int CHOICE_HOVER_COLOR = 0xC0302A1E;
	private static final int NAME_COLOR = 0xFFFFE080;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int NARRATION_COLOR = 0xFFA8A8B8;
	private static final int CHOICE_TEXT_COLOR = 0xFFE0E0E0;
	private static final int CHOICE_HOVER_TEXT_COLOR = 0xFFFFE080;

	private final EntityNPC npc;
	private final Player player;
	private NpcDialogue dialogue;
	private String npcLine;
	private int lineColor = TEXT_COLOR;

	// Layout, recomputed in init() and whenever the line or options change.
	private final List<String> wrappedLine = new ArrayList<>();
	private int boxX;
	private int boxY;
	private int boxWidth;
	private int boxHeight;
	private int choiceX;
	private int choiceY;
	private int choiceWidth;

	public NpcDialogueScreen(EntityNPC npc, Player player, NpcDialogue dialogue) {
		this.npc = npc;
		this.player = player;
		this.dialogue = dialogue;
		this.npcLine = dialogue.line();
	}

	@Override
	public void init() {
		this.npc.setTalkingTo(this.player);
		this.layout();
	}

	private void layout() {
		this.boxWidth = Math.min(this.width - MARGIN * 2, BOX_MAX_WIDTH);
		this.wrappedLine.clear();
		boolean narration = !this.npcLine.isEmpty() && this.npcLine.charAt(0) == NpcDialogue.NARRATION;
		String shown = narration ? this.npcLine.substring(1) : "\"" + this.npcLine + "\"";
		this.fontRenderer.splitCharsIntoLines(shown, this.boxWidth - PADDING * 2, this.wrappedLine);
		this.lineColor = narration ? NARRATION_COLOR : TEXT_COLOR;
		int lines = Math.max(this.wrappedLine.size(), BOX_MIN_LINES);
		this.boxHeight = PADDING * 2 + LINE_HEIGHT + 4 + lines * LINE_HEIGHT;
		this.boxX = (this.width - this.boxWidth) / 2;
		this.boxY = this.height - MARGIN - this.boxHeight;

		List<DialogueOption> options = this.dialogue.options();
		int widest = CHOICE_MIN_WIDTH;
		for (int i = 0; i < options.size(); i++) {
			widest = Math.max(widest, this.fontRenderer.stringWidth(this.choiceLabel(i)) + PADDING * 2);
		}

		this.choiceWidth = Math.min(widest, this.width / 2 - MARGIN);
		this.choiceX = this.width - MARGIN - this.choiceWidth;
		int total = options.size() * (CHOICE_HEIGHT + CHOICE_GAP) - CHOICE_GAP;
		this.choiceY = Math.max(MARGIN, Math.min((this.height - total) / 2, this.boxY - MARGIN - total));
	}

	private String choiceLabel(int index) {
		return (index + 1) + ". " + this.dialogue.options().get(index).text();
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		this.drawBox(this.boxX, this.boxY, this.boxWidth, this.boxHeight, BOX_COLOR);
		this.drawStringShadow(this.fontRenderer, this.npc.nickname, this.boxX + PADDING, this.boxY + PADDING, NAME_COLOR);
		int lineY = this.boxY + PADDING + LINE_HEIGHT + 4;
		for (String line : this.wrappedLine) {
			this.drawStringShadow(this.fontRenderer, line, this.boxX + PADDING, lineY, this.lineColor);
			lineY += LINE_HEIGHT;
		}

		int hovered = this.choiceAt(mx, my);
		List<DialogueOption> options = this.dialogue.options();
		for (int i = 0; i < options.size(); i++) {
			int y = this.choiceY + i * (CHOICE_HEIGHT + CHOICE_GAP);
			boolean hover = i == hovered;
			this.drawBox(this.choiceX, y, this.choiceWidth, CHOICE_HEIGHT, hover ? CHOICE_HOVER_COLOR : CHOICE_COLOR);
			this.drawStringShadow(this.fontRenderer, this.choiceLabel(i), this.choiceX + PADDING, y + (CHOICE_HEIGHT - 8) / 2,
				hover ? CHOICE_HOVER_TEXT_COLOR : CHOICE_TEXT_COLOR);
		}

		super.render(mx, my, partialTick);
	}

	/** A filled panel with a 1px border. */
	private void drawBox(int x, int y, int w, int h, int fill) {
		this.drawRect(x, y, x + w, y + h, fill);
		this.drawRect(x, y, x + w, y + 1, BORDER_COLOR);
		this.drawRect(x, y + h - 1, x + w, y + h, BORDER_COLOR);
		this.drawRect(x, y + 1, x + 1, y + h - 1, BORDER_COLOR);
		this.drawRect(x + w - 1, y + 1, x + w, y + h - 1, BORDER_COLOR);
	}

	private int choiceAt(int mx, int my) {
		if (mx < this.choiceX || mx >= this.choiceX + this.choiceWidth || my < this.choiceY) {
			return -1;
		}

		int row = (my - this.choiceY) / (CHOICE_HEIGHT + CHOICE_GAP);
		int rowY = this.choiceY + row * (CHOICE_HEIGHT + CHOICE_GAP);
		return row < this.dialogue.options().size() && my < rowY + CHOICE_HEIGHT ? row : -1;
	}

	@Override
	public void mouseClicked(int mx, int my, int buttonNum) {
		int choice = this.choiceAt(mx, my);
		if (buttonNum == 0 && choice >= 0) {
			this.choose(choice);
			return;
		}

		super.mouseClicked(mx, my, buttonNum);
	}

	@Override
	public void keyPressed(char eventCharacter, int eventKey, int mx, int my) {
		int index = eventCharacter - '1';
		if (index >= 0 && index < Math.min(9, this.dialogue.options().size())) {
			this.choose(index);
			return;
		}

		super.keyPressed(eventCharacter, eventKey, mx, my);
	}

	private void choose(int index) {
		this.mc.sndManager.playSound("random.click", SoundCategory.GUI_SOUNDS, 1.0F, 1.0F);
		this.dialogue.options().get(index).action().accept(this);
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
		this.layout();
	}

	@Override
	public void show(NpcDialogue dialogue) {
		this.dialogue = dialogue;
		this.npcLine = dialogue.line();
		this.layout();
	}

	@Override
	public void openInventory() {
		this.mc.displayScreen(new NpcInventoryScreen(this.player, this.npc, this));
	}

	@Override
	public void openProfile() {
		this.mc.displayScreen(new NpcProfileScreen(this, this.npc, this.player));
	}

	@Override
	public void close() {
		this.mc.displayScreen(null);
	}
}
