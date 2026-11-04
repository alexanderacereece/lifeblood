package lifeblood.ai.routine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A daily plan: which activity runs from which hour. Each block lasts until the next one starts, and the last block
 * of the day carries on past midnight until the first one.
 */
public final class Schedule {
	/** From this hour (0 to 23), do this activity. */
	public record Block(int fromHour, String activity) {
	}

	private final String id;
	private final List<Block> blocks = new ArrayList<>();

	public Schedule(String id, Block... blocks) {
		if (blocks.length == 0) {
			throw new IllegalArgumentException("A schedule needs at least one block: " + id);
		}

		this.id = id;
		this.blocks.addAll(List.of(blocks));
		this.blocks.sort(Comparator.comparingInt(Block::fromHour));
	}

	/** A schedule of one activity all day. */
	public static Schedule allDay(String id, String activity) {
		return new Schedule(id, new Block(0, activity));
	}

	public String id() {
		return this.id;
	}

	/** The activity for this hour of the day (0 to 23). */
	public String activityAt(int hour) {
		Block current = this.blocks.get(this.blocks.size() - 1);
		for (Block block : this.blocks) {
			if (block.fromHour() <= hour) {
				current = block;
			}
		}

		return current.activity();
	}
}
