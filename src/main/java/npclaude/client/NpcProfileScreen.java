package npclaude.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import npclaude.entity.EntityNPC;
import npclaude.entity.NpcGender;
import npclaude.personality.NpcPersonality;
import npclaude.personality.Quirk;
import npclaude.personality.Surroundings;
import npclaude.personality.Tastes;
import npclaude.personality.TraitAxis;
import npclaude.personality.TraitTier;
import npclaude.social.Memory;
import npclaude.speech.SpeechFacts;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;

/**
 * What the NPC is like, laid out on one page: its eight traits as sliders between their poles, its quirks, what it
 * loves and hates (once the player has heard about them), and how it feels right now and why. What it thinks of the
 * player isn't here: ask it. Opened by asking it about itself;
 * any click or Escape goes back to the conversation.
 */
@Environment(EnvType.CLIENT)
public class NpcProfileScreen extends Screen {
	private static final int MAX_WIDTH = 400;
	private static final int MAX_HEIGHT = 250;
	private static final int PADDING = 8;
	private static final int LINE = 10;
	private static final int ROW = 13;
	private static final int POLE_WIDTH = 66;
	private static final int BAR_WIDTH = 64;
	/** How far wrapped quirk descriptions are indented under the quirk's name. */
	private static final int QUIRK_INDENT = 8;

	private static final int BOX_COLOR = 0xE0101018;
	private static final int BORDER_COLOR = 0xFF8B7B55;
	private static final int TITLE_COLOR = 0xFFFFE080;
	private static final int HEADING_COLOR = 0xFFC8B070;
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int DIM_COLOR = 0xFF808080;
	private static final int TRACK_COLOR = 0xFF3A3A44;
	private static final int CENTER_COLOR = 0xFF606070;
	private static final int LOVE_COLOR = 0xFF90D070;
	private static final int HATE_COLOR = 0xFFE07060;

	private final EntityNPC npc;
	private final Player player;

	public NpcProfileScreen(@Nullable Screen parent, EntityNPC npc, Player player) {
		super(parent);
		this.npc = npc;
		this.player = player;
	}

	@Override
	public void init() {
		this.npc.setTalkingTo(this.player);
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		int w = Math.min(this.width - 16, MAX_WIDTH);
		int h = Math.min(this.height - 16, MAX_HEIGHT);
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		this.drawPanel(x, y, w, h);

		NpcPersonality personality = this.npc.personality;
		long now = this.npc.world.getWorldTime();
		float mood = this.npc.memories.mood(personality, now);
		this.drawStringShadow(this.fontRenderer, this.npc.nickname, x + PADDING, y + PADDING, TITLE_COLOR);
		List<String> subtitle = new ArrayList<>();
		if (this.npc.getFaction() != null) {
			subtitle.add("one of the " + this.npc.getFaction());
		}

		subtitle.add(this.npc.getGender() == NpcGender.FEMALE ? "female" : "male");
		subtitle.add(this.npc.life.getAgeYears() + (this.npc.life.getAgeYears() == 1 ? " year old" : " years old"));
		subtitle.add(this.npc.life.isBirthday() ? "birthday today!" : "born " + this.npc.life.getBirthday());
		subtitle.add("feeling " + SpeechFacts.moodLabel(mood));
		this.drawStringShadow(this.fontRenderer, this.fit(String.join(" · ", subtitle), w - PADDING * 2), x + PADDING, y + PADDING + LINE + 2, DIM_COLOR);

		int top = y + PADDING + LINE * 2 + 10;
		int left = x + PADDING;
		int rightColumn = x + PADDING + POLE_WIDTH * 2 + BAR_WIDTH + 24;

		// Traits
		this.drawStringShadow(this.fontRenderer, "Personality", left, top, HEADING_COLOR);
		int rowY = top + LINE + 4;
		for (TraitAxis axis : TraitAxis.values()) {
			this.drawTrait(personality, axis, left, rowY);
			rowY += ROW;
		}

		// Mood and why
		rowY += 4;
		this.drawStringShadow(this.fontRenderer, "Mood: " + SpeechFacts.moodLabel(mood), left, rowY, HEADING_COLOR);
		rowY += LINE + 2;
		List<Memory> reasons = new ArrayList<>(this.npc.memories.moodMemories(now));
		reasons.sort((a, b) -> Float.compare(Math.abs(effect(b, now)), Math.abs(effect(a, now))));
		int shown = 0;
		for (Memory memory : reasons) {
			if (shown >= 3 || rowY > y + h - PADDING - LINE) {
				break;
			}

			float effect = effect(memory, now);
			String about = memory.subjectName().isEmpty() ? "" : " (" + memory.subjectName() + ")";
			this.drawStringShadow(this.fontRenderer, (effect >= 0 ? "+ " : "- ") + memory.type().description + about, left, rowY,
				effect >= 0 ? LOVE_COLOR : HATE_COLOR);
			rowY += LINE;
			shown++;
		}

		if (shown == 0) {
			this.drawStringShadow(this.fontRenderer, "nothing much on their mind", left, rowY, DIM_COLOR);
		}

		// Quirks
		int columnWidth = x + w - PADDING - rightColumn;
		int ry = top;
		this.drawStringShadow(this.fontRenderer, "Quirks", rightColumn, ry, HEADING_COLOR);
		ry += LINE + 4;
		if (personality.getQuirks().isEmpty()) {
			this.drawStringShadow(this.fontRenderer, "none to speak of", rightColumn, ry, DIM_COLOR);
			ry += LINE;
		}

		for (Quirk quirk : personality.getQuirks()) {
			// "Greedy - loves money and" / "things": the description wraps under the name rather than getting cut off.
			String name = capitalize(quirk.displayName()) + " ";
			this.drawStringShadow(this.fontRenderer, name, rightColumn, ry, TEXT_COLOR);
			int nameWidth = this.fontRenderer.stringWidth(name);
			List<String> lines = this.wrap("- " + quirk.blurb(), columnWidth - nameWidth, columnWidth - QUIRK_INDENT);
			for (int i = 0; i < lines.size(); i++) {
				this.drawStringShadow(this.fontRenderer, lines.get(i), rightColumn + (i == 0 ? nameWidth : QUIRK_INDENT), ry, DIM_COLOR);
				ry += LINE;
			}
		}

		// Tastes
		Tastes tastes = personality.tastes;
		ry += 4;
		this.drawStringShadow(this.fontRenderer, "Loves", rightColumn, ry, HEADING_COLOR);
		ry += LINE + 2;
		ry = this.drawTaste("food", this.secret("favorite_food", () -> itemName(tastes.favoriteFood())), rightColumn, ry, columnWidth, LOVE_COLOR);
		ry = this.drawTaste("things", this.secret("favorite_thing", () -> itemName(tastes.favoriteThing())), rightColumn, ry, columnWidth, LOVE_COLOR);
		ry = this.drawTaste("weather", this.secret("favorite_weather", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.WEATHER, true))), rightColumn, ry, columnWidth, LOVE_COLOR);
		ry = this.drawTaste("place", this.secret("favorite_biome", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.BIOME, true))), rightColumn, ry, columnWidth, LOVE_COLOR);
		ry = this.drawTaste("season", this.secret("favorite_season", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.SEASON, true))), rightColumn, ry, columnWidth, LOVE_COLOR);
		ry += 4;
		this.drawStringShadow(this.fontRenderer, "Hates", rightColumn, ry, HEADING_COLOR);
		ry += LINE + 2;
		ry = this.drawTaste("food", this.secret("hated_food", () -> itemName(tastes.hatedFood())), rightColumn, ry, columnWidth, HATE_COLOR);
		ry = this.drawTaste("things", this.secret("hated_thing", () -> itemName(tastes.hatedThing())), rightColumn, ry, columnWidth, HATE_COLOR);
		ry = this.drawTaste("weather", this.secret("hated_weather", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.WEATHER, false))), rightColumn, ry, columnWidth, HATE_COLOR);
		ry = this.drawTaste("place", this.secret("hated_biome", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.BIOME, false))), rightColumn, ry, columnWidth, HATE_COLOR);
		this.drawTaste("season", this.secret("hated_season", () -> Surroundings.displayName(tastes.favorite(Surroundings.Kind.SEASON, false))), rightColumn, ry, columnWidth, HATE_COLOR);

		if (y + h + 3 + LINE <= this.height) {
			this.drawStringCenteredShadow(this.fontRenderer, "Click or press Esc to go back", x + w / 2, y + h + 3, DIM_COLOR);
		}

		super.render(mx, my, partialTick);
	}

	/** "timid  [----|-o--]  brave" with the leaning pole brighter, and how strongly under the bar. */
	private void drawTrait(NpcPersonality personality, TraitAxis axis, int x, int y) {
		float value = personality.get(axis);
		// The pole it leans toward lights up: white for a lean, gold for strong.
		int negativeColor = poleColor(-value);
		int positiveColor = poleColor(value);

		String negative = capitalize(axis.negative);
		this.drawStringShadow(this.fontRenderer, negative, x + POLE_WIDTH - this.fontRenderer.stringWidth(negative), y, negativeColor);
		int barX = x + POLE_WIDTH + 6;
		int barY = y + 3;
		this.drawRect(barX, barY, barX + BAR_WIDTH, barY + 3, TRACK_COLOR);
		this.drawRect(barX + BAR_WIDTH / 2, barY - 1, barX + BAR_WIDTH / 2 + 1, barY + 4, CENTER_COLOR);
		int marker = barX + Math.round((value + 1.0F) / 2.0F * (BAR_WIDTH - 3));
		int markerColor = negativeColor == DIM_COLOR && positiveColor == DIM_COLOR ? TEXT_COLOR : value < 0 ? negativeColor : positiveColor;
		this.drawRect(marker, barY - 2, marker + 3, barY + 5, markerColor);
		this.drawStringShadow(this.fontRenderer, capitalize(axis.positive), barX + BAR_WIDTH + 6, y, positiveColor);
	}

	private static int poleColor(float towardPole) {
		if (towardPole >= TraitTier.STRONG_THRESHOLD) {
			return TITLE_COLOR;
		}

		return towardPole >= TraitTier.LEAN_THRESHOLD ? TEXT_COLOR : DIM_COLOR;
	}

	/** The answer if the player has heard the NPC mention it, otherwise a question mark. */
	private String secret(String topic, Supplier<String> answer) {
		return this.npc.voice.isRevealed(topic) ? answer.get() : "?";
	}

	private int drawTaste(String label, String value, int x, int y, int width, int color) {
		this.drawStringShadow(this.fontRenderer, label + ":", x, y, DIM_COLOR);
		int labelWidth = this.fontRenderer.stringWidth(label + ": ");
		this.drawStringShadow(this.fontRenderer, this.fit(value, width - labelWidth), x + labelWidth, y, color);
		return y + LINE;
	}

	private void drawPanel(int x, int y, int w, int h) {
		this.drawRect(x, y, x + w, y + h, BOX_COLOR);
		this.drawRect(x, y, x + w, y + 1, BORDER_COLOR);
		this.drawRect(x, y + h - 1, x + w, y + h, BORDER_COLOR);
		this.drawRect(x, y + 1, x + 1, y + h - 1, BORDER_COLOR);
		this.drawRect(x + w - 1, y + 1, x + w, y + h - 1, BORDER_COLOR);
	}

	/** Word-wraps text: the first line gets one width, the rest another. */
	private List<String> wrap(String text, int firstWidth, int restWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String attempt = line.isEmpty() ? word : line + " " + word;
			int width = lines.isEmpty() ? firstWidth : restWidth;
			if (!line.isEmpty() && this.fontRenderer.stringWidth(attempt) > width) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(attempt);
			}
		}

		lines.add(line.toString());
		return lines;
	}

	/** Cuts text to fit on one line, ending in "..." if it had to be cut. (The game's own version wraps instead.) */
	private String fit(String text, int width) {
		if (this.fontRenderer.stringWidth(text) <= width) {
			return text;
		}

		String cut = text;
		while (!cut.isEmpty() && this.fontRenderer.stringWidth(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}

		return cut.trim() + "...";
	}

	private static float effect(Memory memory, long now) {
		return memory.type().mood * memory.scale() * memory.strength(now);
	}

	private static String itemName(@Nullable Item item) {
		return item != null ? new ItemStack(item).getDisplayName().toLowerCase(Locale.ROOT) : "nothing in particular";
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	@Override
	public void mouseClicked(int mx, int my, int buttonNum) {
		this.mc.displayScreen(this.parentScreen);
	}

	@Override
	public void keyPressed(char eventCharacter, int eventKey, int mx, int my) {
		if (eventKey == Keyboard.KEY_ESCAPE || eventKey == Keyboard.KEY_BACK) {
			this.mc.displayScreen(this.parentScreen);
		}
	}

	@Override
	public void tick() {
		if (!this.npc.isAlive() || this.npc.getTalkingTo() != this.player) {
			this.mc.displayScreen(null);
		}
	}

	@Override
	public void removed() {
		this.npc.endTalkSoon(this.player);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
