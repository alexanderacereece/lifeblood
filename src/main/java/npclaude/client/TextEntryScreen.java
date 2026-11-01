package npclaude.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.TextFieldElement;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

/**
 * Asks the player to type something (a word for an NPC to learn): the question, a text box, and Done / Cancel,
 * plus an optional extra button (like "Can't count it"). Enter means Done, Escape means Cancel.
 */
@Environment(EnvType.CLIENT)
public class TextEntryScreen extends Screen {
	private static final int MAX_LENGTH = 24;
	private static final int BOX_COLOR = 0xE0101018;
	private static final int BORDER_COLOR = 0xFF8B7B55;
	private static final int TEXT_COLOR = 0xFFE0E0E0;

	private final String question;
	private final String initial;
	@Nullable
	private final String extraLabel;
	private final Consumer<String> onDone;
	@Nullable
	private final Runnable onExtra;
	private final Runnable onCancel;
	private TextFieldElement field;
	private final List<String> questionLines = new ArrayList<>();

	public TextEntryScreen(Screen parent, String question, String initial, @Nullable String extraLabel, Consumer<String> onDone,
		@Nullable Runnable onExtra, Runnable onCancel) {
		super(parent);
		this.question = question;
		this.initial = initial;
		this.extraLabel = extraLabel;
		this.onDone = onDone;
		this.onExtra = onExtra;
		this.onCancel = onCancel;
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		this.buttons.clear();
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		this.questionLines.clear();
		this.fontRenderer.splitCharsIntoLines(this.question, 220, this.questionLines);
		String text = this.field != null ? this.field.getText() : this.initial;
		this.field = new TextFieldElement(this, this.fontRenderer, centerX - 100, centerY - 10, 200, 20, text, "");
		this.field.setMaxStringLength(MAX_LENGTH);
		this.field.setFocused(true);
		boolean extra = this.extraLabel != null;
		int buttonWidth = extra ? 64 : 98;
		int left = centerX - (extra ? 100 : 100);
		this.add(new ButtonElement(0, left, centerY + 18, buttonWidth, 20, "Done"));
		if (extra) {
			this.add(new ButtonElement(2, left + buttonWidth + 4, centerY + 18, buttonWidth, 20, this.extraLabel));
		}

		this.add(new ButtonElement(1, centerX + 100 - buttonWidth, centerY + 18, buttonWidth, 20, "Cancel"));
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
	}

	@Override
	public void tick() {
		this.field.updateCursorCounter();
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		int top = centerY - 22 - this.questionLines.size() * 10;
		this.drawRect(centerX - 112, top - 6, centerX + 112, centerY + 46, BOX_COLOR);
		this.drawRect(centerX - 112, top - 6, centerX + 112, top - 5, BORDER_COLOR);
		this.drawRect(centerX - 112, centerY + 45, centerX + 112, centerY + 46, BORDER_COLOR);
		for (int i = 0; i < this.questionLines.size(); i++) {
			this.drawStringCenteredShadow(this.fontRenderer, this.questionLines.get(i), centerX, top + i * 10, TEXT_COLOR);
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
