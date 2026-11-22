package org.mewx.wenku8.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Per-account Cloud Bookcase synchronization ledger (`cloud_bookcase_ledger` in `wenku8_v2.db`).
 *
 * Why a dedicated per-account ledger without a cascading foreign key to `novels`:
 * 1. **Prevents Resurrecting Locally-Deleted Novels**:
 *    When a user removes a novel from their bookshelf while offline, on a flaky connection, or even
 *    followed by clearing non-bookshelf browse caches (`deleteAllNonBookshelfNovels`), the durable
 *    [STATE_PENDING_DELETE] tombstone survives in SQLite. The next `syncWithCloudBookcase` pass
 *    replays `action=bookcase&do=del&aid={aid}` first and blocks re-hydrating that `aid` even if
 *    the delete call transiently fails.
 * 2. **Prevents Resurrecting Remotely-Deleted Novels**:
 *    A novel recorded as [STATE_SYNCED] under `account_key` was previously confirmed present on
 *    that exact Wenku8 account's cloud bookcase. If a subsequent valid `action=bookcase` listing
 *    for the same `account_key` no longer includes `aid`, the novel was deleted on Wenku8 Web or
 *    another device — so `:app-v2` removes it from the local shelf (`in_bookshelf = 0`, preserving
 *    reading progress, bookmarks, and cached chapters) instead of pushing it back up to the cloud.
 * 3. **Prevents Overriding / Cross-Account Shelf Pollution**:
 *    Scoping sync state by normalized `account_key` ensures switching from Account A to Account B
 *    neither wipes Account A's local books nor silently uploads Account A's cloud books into
 *    Account B's cloud bookcase.
 */
@Entity(
    tableName = "cloud_bookcase_ledger",
    primaryKeys = ["account_key", "aid"],
    indices = [
        Index(value = ["account_key", "sync_state"]),
        Index(value = ["aid"]),
    ],
)
data class CloudBookcaseLedgerEntity(
    @ColumnInfo(name = "account_key")
    val accountKey: String,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "sync_state")
    val syncState: String,

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
) {
    companion object {
        const val STATE_SYNCED = "SYNCED"
        const val STATE_PENDING_ADD = "PENDING_ADD"
        const val STATE_PENDING_DELETE = "PENDING_DELETE"
        const val DEFAULT_TEST_ACCOUNT_KEY = "__default_account__"
    }
}
