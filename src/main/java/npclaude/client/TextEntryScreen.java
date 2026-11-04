package npclaude.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.TextFieldElement;
import npclaude.dialogue.DialogueSession;
import npclaude.dialogue.TextRequest;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

/**
 * Asks the player to type something (a word for an NPC to learn): the question, a hint about what kind of answer
 * fits, a text box with any lead-in words before it ("a ___"), and Done / Cancel, plus an optional extra button
 * (like "Can't count it"). Enter means Done, Escape means Cancel.
 */
@Environment(EnvType.CLIENT)
public class TextEntryScreen extends Screen {
	private static final int MAX_LENGTH = 24;
	private static final int HALF_WIDTH = 124;
	private static final int BOX_COLOR = 0xE0101018;
	private static final int BORDER_COLOR = 0xFF8B7B55;
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int HINT_COLOR = 0xFFA8A8B8;

	private final TextRequest request;
	private final Consumer<String> onDone;
	@Nullable
	private final Runnable onExtra;
	private final Runnable onCancel;
	private TextFieldElement field;
	private final List<String> questionLines = new ArrayList<>();
	private final List<String> hintLines = new ArrayList<>();

	public TextEntryScreen(Screen parent, TextRequest request, Consumer<String> onDone, @Nullable Runnable onExtra, Runnable onCancel) {
		super(parent);
		this.request = request;
		this.onDone = onDone;
		this.onExtra = onExtra;
		this.onCancel = onCancel;
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		// Typing a reply is still part of the conversation: the NPC keeps waiting.
		if (this.parentScreen instanceof DialogueSession session) {
			session.getNpc().setTalkingTo(session.getPlayer());
		}

		this.buttons.clear();
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		this.questionLines.clear();
		this.hintLines.clear();
		this.fontRenderer.splitCharsIntoLines(this.request.question(), HALF_WIDTH * 2 - 16, this.questionLines);
		if (!this.request.hint().isEmpty()) {
			this.fontRenderer.splitCharsIntoLines(this.request.hint(), HALF_WIDTH * 2 - 16, this.hintLines);
		}

		String text = this.field != null ? this.field.getText() : this.request.initial();
		int prefixWidth = this.request.prefix().isEmpty() ? 0 : this.fontRenderer.stringWidth(this.request.prefix()) + 6;
		this.field = new TextFieldElement(this, this.fontRenderer, centerX - HALF_WIDTH + 8 + prefixWidth, centerY - 10,
			HALF_WIDTH * 2 - 16 - prefixWidth, 20, text, "");
		this.field.setMaxStringLength(MAX_LENGTH);
		this.field.setFocused(true);
		boolean extra = this.request.extraLabel() != null;
		int left = centerX - HALF_WIDTH + 8;
		int span = HALF_WIDTH * 2 - 16;
		if (extra) {
			int width = (span - 8) / 3;
			this.add(new ButtonElement(0, left, centerY + 18, width, 20, "Done"));
			this.add(new ButtonElement(2, left + width + 4, centerY + 18, width, 20, this.request.extraLabel()));
			this.add(new ButtonElement(1, left + (width + 4) * 2, centerY + 18, width, 20, "Cancel"));
		} else {
			int width = (span - 4) / 2;
			this.add(new ButtonElement(0, left, centerY + 18, width, 20, "Done"));
			this.add(new ButtonElement(1, left + width + 4, centerY + 18, width, 20, "Cancel"));
		}
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
		if (this.parentScreen instanceof DialogueSession session) {
			session.getNpc().endTalkSoon(session.getPlayer());
		}
	}

	@Override
	public void tick() {
		this.field.updateCursorCounter();
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int lines = this.questionLines.size() + this.hintLines.size();
		int top = centerY - 18 - lines * 10;
		this.drawRect(centerX - HALF_WIDTH, top - 6, centerX + HALF_WIDTH, centerY + 46, BOX_COLOR);
		this.drawRect(centerX - HALF_WIDTH, top - 6, centerX + HALF_WIDTH, top - 5, BORDER_COLOR);
		this.drawRect(centerX - HALF_WIDTH, centerY + 45, centerX + HALF_WIDTH, centerY + 46, BORDER_COLOR);
		int y = top;
		for (String line : this.questionLines) {
			this.drawStringCenteredShadow(this.fontRenderer, line, centerX, y, TEXT_COLOR);
			y += 10;
		}

		for (String line : this.hintLines) {
			this.drawStringCenteredShadow(this.fontRenderer, line, centerX, y, HINT_COLOR);
			y += 10;
		}

		if (!this.request.prefix().isEmpty()) {
			this.drawStringShadow(this.fontRenderer, this.request.prefix(), centerX - HALF_WIDTH + 8, centerY - 4, TEXT_COLOR);
		}

		this.field.drawTextBox();
		super.render(mx, my, partialTick);
	}

	@Override
	public void keyPressed(char eventCharacter, int eventKey, int mx, int my) {
		if (eventKey == Keyboard.KEY_RETURN || eventKey == Keyboard.KEY_NUMPADENTER) {
			this.done();
		} else if (eventKey == Keyboard.KEY_ESCAPE) {
			this.cancel();
		} else {
			this.field.textboxKeyTyped(eventCharacter, eventKey);
		}
	}

	@Override
	public void mouseClicked(int mx, int my, int buttonNum) {
		this.field.mouseClicked(mx, my, buttonNum);
		super.mouseClicked(mx, my, buttonNum);
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		switch (button.id) {
			case 0 -> this.done();
			case 2 -> {
				this.mc.displayScreen(this.parentScreen);
				if (this.onExtra != null) {
					this.onExtra.run();
				}
			}
			default -> this.cancel();
		}
	}

	private void done() {
		String text = this.field.getText().strip();
		if (text.isEmpty()) {
			return;
		}

		this.mc.displayScreen(this.parentScreen);
		this.onDone.accept(text);
	}

	private void cancel() {
		this.mc.displayScreen(this.parentScreen);
		this.onCancel.run();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
