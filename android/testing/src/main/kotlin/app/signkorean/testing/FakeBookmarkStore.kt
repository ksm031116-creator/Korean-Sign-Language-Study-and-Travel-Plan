package app.signkorean.testing

import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.content.CardId
import kotlinx.coroutines.yield

class FakeBookmarkStore(
    initial: List<Bookmark> = emptyList(),
    private val yieldOnAccess: Boolean = false,
) : BookmarkStore, FakeTransactional {
    private val items = initial.associateBy { it.card }.toMutableMap()
    var failNextWrite = false

    private suspend fun pause() {
        if (yieldOnAccess) yield()
    }

    override suspend fun all(): List<Bookmark> {
        pause()
        return items.values.sortedByDescending { it.createdAt }
    }

    override suspend fun count(): Int {
        pause()
        return items.size
    }

    override suspend fun contains(card: CardId): Boolean {
        pause()
        return card in items
    }

    override suspend fun add(bookmark: Bookmark) {
        pause()
        if (failNextWrite) {
            failNextWrite = false
            throw FakeStorageException("bookmark")
        }
        items.putIfAbsent(bookmark.card, bookmark)
    }

    override suspend fun remove(card: CardId) {
        pause()
        items.remove(card)
    }

    override suspend fun clear() {
        if (failNextWrite) {
            failNextWrite = false
            throw FakeStorageException("bookmark clear")
        }
        items.clear()
    }

    override fun snapshot(): Any = items.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun rollback(snapshot: Any) {
        items.clear()
        items.putAll(snapshot as Map<CardId, Bookmark>)
    }
}
