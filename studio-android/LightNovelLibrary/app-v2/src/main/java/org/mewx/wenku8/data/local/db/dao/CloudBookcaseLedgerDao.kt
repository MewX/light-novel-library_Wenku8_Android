package org.mewx.wenku8.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import org.mewx.wenku8.data.local.db.entity.CloudBookcaseLedgerEntity

@Dao
interface CloudBookcaseLedgerDao {

    @Upsert
    suspend fun upsertEntry(entry: CloudBookcaseLedgerEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<CloudBookcaseLedgerEntity>)

    @Query("SELECT * FROM cloud_bookcase_ledger WHERE account_key = :accountKey")
    suspend fun getEntriesForAccount(accountKey: String): List<CloudBookcaseLedgerEntity>

    @Query(
        """
        SELECT * FROM cloud_bookcase_ledger
        WHERE account_key = :accountKey AND aid = :aid
        LIMIT 1
        """,
    )
    suspend fun getEntry(accountKey: String, aid: Int): CloudBookcaseLedgerEntity?

    @Query("SELECT * FROM cloud_bookcase_ledger WHERE aid IN (:aids)")
    suspend fun getEntriesForAids(aids: List<Int>): List<CloudBookcaseLedgerEntity>

    @Query(
        """
        SELECT DISTINCT aid FROM cloud_bookcase_ledger
        WHERE account_key != :currentAccountKey
          AND sync_state = :syncedState
        """,
    )
    suspend fun getAidsSyncedWithOtherAccounts(
        currentAccountKey: String,
        syncedState: String = CloudBookcaseLedgerEntity.STATE_SYNCED,
    ): List<Int>

    @Query("DELETE FROM cloud_bookcase_ledger WHERE account_key = :accountKey AND aid = :aid")
    suspend fun deleteEntry(accountKey: String, aid: Int)

    @Query(
        """
        DELETE FROM cloud_bookcase_ledger
        WHERE account_key = :accountKey
          AND aid = :aid
          AND sync_state = :expectedState
        """,
    )
    suspend fun deleteEntryIfStateMatches(accountKey: String, aid: Int, expectedState: String)

    @Query("DELETE FROM cloud_bookcase_ledger WHERE aid IN (:aids)")
    suspend fun clearEntriesForAids(aids: List<Int>)

    /**
     * Migrates offline ledger records created under an email login hint (`fromAliasKey`) into the
     * canonical Wenku8 `uname` (`toCanonicalKey`) once authenticated profile resolution succeeds,
     * keeping whichever record has the newer `updated_at_ms`.
     */
    @Transaction
    suspend fun migrateAccountAliasEntries(fromAliasKey: String, toCanonicalKey: String) {
        if (fromAliasKey.isBlank() || toCanonicalKey.isBlank() || fromAliasKey == toCanonicalKey) {
            return
        }
        val aliasEntries = getEntriesForAccount(fromAliasKey)
        if (aliasEntries.isEmpty()) return

        for (aliasEntry in aliasEntries) {
            val existingCanonical = getEntry(toCanonicalKey, aliasEntry.aid)
            if (existingCanonical == null || aliasEntry.updatedAtMs >= existingCanonical.updatedAtMs) {
                upsertEntry(aliasEntry.copy(accountKey = toCanonicalKey))
            }
            deleteEntry(fromAliasKey, aliasEntry.aid)
        }
    }
}
