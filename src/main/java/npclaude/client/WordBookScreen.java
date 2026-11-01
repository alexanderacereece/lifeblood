package npclaude.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.player.Player;
import npclaude.family.FamilyRegistry;
import npclaude.words.WordBook;

/**
 * The word book: every word players have taught, newest first, a page at a time. Each shows its category, whether
 * it's personal or general, who it was taught to and by whom, and how many NPCs know it. Delete (clicked twice to
 * confirm) removes a word from everyone.
 */
@Environment(EnvType.CLIENT)
public class WordBookScreen extends Screen {
	private static final int ROWS = 7;
	private static final int ROW_HEIGHT = 24;
	private static final int HALF_WIDTH = 170;
	private static final int BOX_COLOR = 0xE0101018;
	private static final int BORDER_COLOR = 0xFF8B7B55;
	private static final int TITLE_COLOR = 0xFFFFE080;
	private static final int WORD_COLOR = 0xFFFFFFFF;
	private static final int DETAIL_COLOR = 0xFFA8A8B8;
	private static final int ID_PREVIOUS = 1000;
	private static final int ID_NEXT = 1001;
	private static final int ID_DONE = 1002;

	private final Player player;
	private final List<WordBook.TaughtWord> words = new ArrayList<>();
	private int page;
	/** A word whose delete button was clicked once and now asks "Sure?". */
	private int confirming = -1;

	public WordBookScreen(Player player) {
		this.player = player;
	}

	@Override
	public void init() {
		this.words.clear();
		this.words.addAll(WordBook.get(this.player.world).all());
		this.words.sort((a, b) -> Integer.compare(b.id, a.id));
		int pages = Math.max(1, (this.words.size() + ROWS - 1) / ROWS);
		this.page = Math.min(this.page, pages - 1);
		this.buttons.clear();
		int top = this.top();
		int right = this.width / 2 + HALF_WIDTH - 8;
		for (int i = 0; i < ROWS; i++) {
			int index = this.page * ROWS + i;
			if (index >= this.words.size()) {
				break;
			}

			WordBook.TaughtWord word = this.words.get(index);
			this.add(new ButtonElement(word.id, right - 50, top + 22 + i * ROW_HEIGHT, 50, 20, word.id == this.confirming ? "Sure?" : "Delete"));
		}

		int bottom = top + 22 + ROWS * ROW_HEIGHT + 4;
		ButtonElement previous = this.add(new ButtonElement(ID_PREVIOUS, this.width / 2 - HALF_WIDTH + 8, bottom, 60, 20, "< Back"));
		ButtonElement next = this.add(new ButtonElement(ID_NEXT, this.width / 2 + HALF_WIDTH - 68, bottom, 60, 20, "Next >"));
		previous.enabled = this.page > 0;
		next.enabled = this.page < pages - 1;
		this.add(new ButtonElement(ID_DONE, this.width / 2 - 40, bottom, 80, 20, "Done"));
	}

	private int top() {
		return Math.max(4, this.height / 2 - (22 + ROWS * ROW_HEIGHT + 28) / 2);
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		int top = this.top();
		int left = this.width / 2 - HALF_WIDTH;
		int bottom = top + 22 + ROWS * ROW_HEIGHT + 28;
		this.drawRect(left, top, left + HALF_WIDTH * 2, bottom, BOX_COLOR);
		this.drawRect(left, top, left + HALF_WIDTH * 2, top + 1, BORDER_COLOR);
		this.drawRect(left, bottom - 1, left + HALF_WIDTH * 2, bottom, BORDER_COLOR);
		int pages = Math.max(1, (this.words.size() + ROWS - 1) / ROWS);
		this.drawStringShadow(this.fontRenderer, "Word Book", left + 8, top + 7, TITLE_COLOR);
		String count = this.words.size() + (this.words.size() == 1 ? " word" : " words") + ", page " + (this.page + 1) + " of " + pages;
		this.drawStringShadow(this.fontRenderer, count, left + HALF_WIDTH * 2 - 8 - this.fontRenderer.stringWidth(count), top + 7, DETAIL_COLOR);
		if (this.words.isEmpty()) {
			this.drawStringCenteredShadow(this.fontRenderer, "No words yet. Chat with people: they'll ask you for some.", this.width / 2,
				top + 22 + ROW_HEIGHT * 2, DETAIL_COLOR);
		}

		FamilyRegistry family = FamilyRegistry.get(this.player.world);
		for (int i = 0; i < ROWS; i++) {
			int index = this.page * ROWS + i;
			if (index >= this.words.size()) {
				break;
			}

			WordBook.TaughtWord word = this.words.get(index);
			int y = top + 22 + i * ROW_HEIGHT;
			String forms = word.word.singular() + (word.word.plural().equals(word.word.singular()) ? "" : " / " + word.word.plural());
			this.drawStringShadow(this.fontRenderer, forms, left + 8, y + 1, WORD_COLOR);
			UUID first = word.knownBy.isEmpty() ? null : word.knownBy.keySet().iterator().next();
			String taughtTo = first != null ? family.nameOf(first) : "someone";
			String details = word.category.displayName() + ", " + (word.personal ? "just for " + taughtTo : "general") + ". Taught to " + taughtTo
				+ " by " + word.teacherName + (word.personal ? "" : "; " + word.knownBy.size() + " know it");
			this.drawStringShadow(this.fontRenderer, this.fit(details, HALF_WIDTH * 2 - 74), left + 8, y + 11, DETAIL_COLOR);
		}

		super.render(mx, my, partialTick);
	}

	private String fit(String text, int width) {
		if (this.fontRenderer.stringWidth(text) <= width) {
			return text;
		}

		String cut = text;
		while (!cut.isEmpty() && this.fontRenderer.stringWidth(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}

		return cut.strip() + "...";
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		switch (button.id) {
			case ID_PREVIOUS -> this.page--;
			case ID_NEXT -> this.page++;
			case ID_DONE -> {
				this.mc.displayScreen(null);
				return;
			}
			default -> {
				if (this.confirming == button.id) {
					WordBook.get(this.player.world).delete(button.id);
					this.confirming = -1;
				} else {
					this.confirming = button.id;
				}
			}
		}

		this.init();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
