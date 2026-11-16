package org.mewx.wenku8.v2.di

import android.content.Context
import android.os.Environment
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.v2.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.v2.data.migration.InternalFileSaveSource
import org.mewx.wenku8.v2.data.migration.LegacyMigrationProgress
import org.mewx.wenku8.v2.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import java.io.File

/**
 * Lightweight, explicit constructor-injection graph for Wenku8 `:app-v2`.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: Wenku8Database by lazy {
        Wenku8Database.createWalDatabase(appContext)
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository.create(appContext, applicationScope)
    }

    val novelTocSyncRepository: NovelTocSyncRepository by lazy {
        NovelTocSyncRepository(database)
    }

    val legacyV1StorageMigrator: LegacyV1StorageMigrator by lazy {
        LegacyV1StorageMigrator(database, userPreferencesRepository)
    }

    private val _migrationProgressFlow = MutableStateFlow<LegacyMigrationProgress?>(null)
    val migrationProgressFlow: StateFlow<LegacyMigrationProgress?> = _migrationProgressFlow.asStateFlow()

    /**
     * Ordered local asset roots (`filesDir` first, optional legacy `/sdcard/wenku8` second)
     * searched by [OfflineFirstWenkuImageFetcher] and [InternalFileSaveSource].
     */
    fun resolveLocalAssetRoots(): List<File> {
        val legacyExternalDir = runCatching {
            File(Environment.getExternalStorageDirectory(), "wenku8")
        }.getOrNull()
        return listOfNotNull(
            appContext.filesDir,
            legacyExternalDir?.takeIf { it.isDirectory && it.canRead() },
        )
    }

    fun buildOfflineFirstImageLoader(): ImageLoader {
        return ImageLoader.Builder(appContext)
            .components {
                add(OfflineFirstWenkuImageFetcher.Factory(::resolveLocalAssetRoots))
            }
            .build()
    }

    /**
     * Runs automatic non-blocking `v1.31 -> v2.0` internal-storage migration once on cold start
     * when upgrading an existing installation (`legacyV1InternalMigrationVersion < 2`).
     */
    fun triggerAutomaticInternalMigrationIfNeeded() {
        applicationScope.launch {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            if (prefs.legacyV1InternalMigrationVersion >= UserPreferencesRepository.CURRENT_V1_MIGRATION_VERSION) {
                return@launch
            }
            val roots = resolveLocalAssetRoots()
            val primaryRoot = roots.firstOrNull() ?: return@launch
            val fallbackRoot = roots.getOrNull(1)
            val source = InternalFileSaveSource(
                primaryInternalRoot = primaryRoot,
                fallbackExternalRoot = fallbackRoot,
            )
            try {
                legacyV1StorageMigrator.migrateFromSource(
                    source = source,
                    onProgress = { progress -> _migrationProgressFlow.value = progress },
                )
            } finally {
                _migrationProgressFlow.value = null
            }
        }
    }
}
