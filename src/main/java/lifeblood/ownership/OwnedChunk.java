package lifeblood.ownership;

/** Added to every chunk by a mixin: the records of who placed its blocks. */
public interface OwnedChunk {
	ChunkOwners lifeblood$owners();
}
