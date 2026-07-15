package dev.gatsyuk.grindsync.core.export

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Location of automatic backups: the app's external files dir, which is
 * user-visible in any file manager (Android/data/dev.gatsyuk.grindsync/files/
 * backups) and needs no runtime permission. Removed on uninstall — the manual
 * "Export" flow via SAF is the durable, off-device copy.
 */
object BackupFiles {

    private const val DIR = "backups"
    private const val PREFIX = "solo-ranking-backup-"
    // Pre-rename installs (<= 0.6.2) wrote this prefix; still list/prune those files.
    private const val LEGACY_PREFIX = "grindsync-backup-"
    private const val SUFFIX = ".json"
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

    fun backupDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR).apply { mkdirs() }

    fun newBackupFile(context: Context, now: Instant = Instant.now()): File =
        File(backupDir(context), "$PREFIX${stamp.format(now)}$SUFFIX")

    fun listBackups(context: Context): List<File> =
        backupDir(context).listFiles { f ->
            (f.name.startsWith(PREFIX) || f.name.startsWith(LEGACY_PREFIX)) && f.name.endsWith(SUFFIX)
        }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Keep the newest [keep] backups; delete the rest. */
    fun prune(context: Context, keep: Int = 10) {
        listBackups(context).drop(keep).forEach { it.delete() }
    }
}
