package org.mewx.wenku8.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.Wenku8V2Application

/**
 * AndroidX [CoroutineWorker] executing [NovelDownloadRepository.executeDownload] reliably in the
 * background when the user navigates away from `NovelDetailScreen` or backgrounds the app.
 */
class NovelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val aid = inputData.getInt(KEY_AID, 0)
        if (aid <= 0) return Result.failure()

        val languageName = inputData.getString(KEY_APP_LANGUAGE).orEmpty()
        val appLanguage = Wenku8API.AppLanguage.entries.firstOrNull { it.name == languageName }
            ?: Wenku8API.AppLanguage.SC

        val modeName = inputData.getString(KEY_DOWNLOAD_MODE).orEmpty()
        val mode = NovelDownloadMode.entries.firstOrNull { it.name == modeName }
            ?: NovelDownloadMode.SMART_UPDATE

        val selectedVids = inputData.getIntArray(KEY_SELECTED_VIDS)?.toSet().orEmpty()
        val overwriteSelected = inputData.getBoolean(KEY_OVERWRITE_SELECTED, false)

        val application = applicationContext as? Wenku8V2Application ?: return Result.failure()
        val repository = application.appContainer.novelDownloadRepository

        val outcome = repository.executeDownload(
            NovelDownloadRequest(
                aid = aid,
                appLanguage = appLanguage,
                mode = mode,
                selectedVolumeIds = selectedVids,
                overwriteSelectedVolumes = overwriteSelected,
            ),
        )

        return when (outcome.state) {
            NovelDownloadRunState.SUCCEEDED,
            NovelDownloadRunState.PARTIAL_FAILURE,
            -> Result.success()

            NovelDownloadRunState.FAILED -> if (runAttemptCount < 2) Result.retry() else Result.failure()
            NovelDownloadRunState.CANCELLED,
            NovelDownloadRunState.RUNNING,
            -> Result.failure()
        }
    }

    companion object {
        const val KEY_AID = "aid"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_DOWNLOAD_MODE = "download_mode"
        const val KEY_SELECTED_VIDS = "selected_vids"
        const val KEY_OVERWRITE_SELECTED = "overwrite_selected"
    }
}
