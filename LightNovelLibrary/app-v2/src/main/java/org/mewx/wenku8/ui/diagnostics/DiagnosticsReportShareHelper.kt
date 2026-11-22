package org.mewx.wenku8.ui.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsReport
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Exports `wenku8-network-report-v1` JSON attachments into app-private `cache/diagnostics/` with
 * 24-hour auto-purge and read-only `FileProvider` URI grants (`FLAG_GRANT_READ_URI_PERMISSION`,
 * never write permission).
 */
object DiagnosticsReportShareHelper {

    const val CACHE_SUBDIR_NAME: String = "diagnostics"
    const val MAX_ATTACHMENT_AGE_MILLIS: Long = 24L * 60L * 60L * 1_000L

    fun providerAuthority(context: Context): String =
        "${context.packageName}.diagnostics.fileprovider"

    fun purgeExpiredReportFiles(
        cacheRootDir: File,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Int {
        val dir = File(cacheRootDir, CACHE_SUBDIR_NAME)
        if (!dir.isDirectory) return 0
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && nowEpochMillis - file.lastModified() >= MAX_ATTACHMENT_AGE_MILLIS) {
                if (file.delete()) {
                    deletedCount += 1
                }
            }
        }
        return deletedCount
    }

    fun writeReportJsonToCache(
        cacheRootDir: File,
        report: DiagnosticsReport,
        optionalUserNote: String?,
    ): File {
        val dir = File(cacheRootDir, CACHE_SUBDIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val shortRunId = report.runId.filter { it.isLetterOrDigit() }.take(8).ifEmpty { "report" }
        val targetFile = File(dir, "wenku8-network-report-$shortRunId.json")
        val jsonBytes = report
            .toRedactedJsonString(optionalUserNote = optionalUserNote)
            .toByteArray(StandardCharsets.UTF_8)
        targetFile.writeBytes(jsonBytes)
        return targetFile
    }

    fun createChooserShareIntent(
        context: Context,
        report: DiagnosticsReport,
        optionalUserNote: String?,
        chooserTitle: String,
    ): Intent {
        val exportedFile = writeReportJsonToCache(
            cacheRootDir = context.cacheDir,
            report = report,
            optionalUserNote = optionalUserNote,
        )
        val contentUri = FileProvider.getUriForFile(
            context,
            providerAuthority(context),
            exportedFile,
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, report.toPlainTextSummary())
            clipData = ClipData.newRawUri("", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }
}
