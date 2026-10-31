package npclaude.nav;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** A planned route, followed one step at a time. */
public final class NavPath {
	private final List<NavStep> steps;
	private int index;
	/** False if the search ran out of budget and this only gets as close as it could. */
	public final boolean complete;

	public NavPath(List<NavStep> steps, boolean complete) {
		this.steps = steps;
		this.complete = complete;
	}

	@Nullable
	public NavStep current() {
		return this.index < this.steps.size() ? this.steps.get(this.index) : null;
	}

	@Nullable
	public NavStep previous() {
		return this.index > 0 ? this.steps.get(this.index - 1) : null;
	}

	@Nullable
	public NavStep peek(int ahead) {
		int i = this.index + ahead;
		return i < this.steps.size() ? this.steps.get(i) : null;
	}

	public NavStep last() {
		return this.steps.get(this.steps.size() - 1);
	}

	public void advance() {
		this.index++;
	}

	public boolean isDone() {
		return this.index >= this.steps.size();
	}

	public int remaining() {
		return this.steps.size() - this.index;
	}
}
