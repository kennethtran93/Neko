package eu.kanade.tachiyomi.data.notification

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupRestoreService
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.DiskUtil
import eu.kanade.tachiyomi.util.getUriCompat
import eu.kanade.tachiyomi.util.notificationManager
import eu.kanade.tachiyomi.util.toast
import java.io.File
import uy.kohesive.injekt.injectLazy

/**
 * Global [BroadcastReceiver] that runs on UI thread
 * Pending Broadcasts should be made from here.
 * NOTE: Use local broadcasts if possible.
 */
class NotificationReceiver : BroadcastReceiver() {
    /**
     * Download manager.
     */
    private val downloadManager: DownloadManager by injectLazy()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Dismiss notification
            ACTION_DISMISS_NOTIFICATION -> dismissNotification(
                context,
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            )
            // Resume the download service
            ACTION_RESUME_DOWNLOADS -> DownloadService.start(context)
            // Pause the download service
            ACTION_PAUSE_DOWNLOADS -> {
                DownloadService.stop(context)
                downloadManager.pauseDownloads()
            }
            // Clear the download queue
            ACTION_CLEAR_DOWNLOADS -> downloadManager.clearQueue(true)
            // Show message notification created
            ACTION_SHORTCUT_CREATED -> context.toast(R.string.shortcut_created)
            // Launch share activity and dismiss notification
            ACTION_SHARE_IMAGE -> shareImage(
                context, intent.getStringExtra(EXTRA_FILE_LOCATION),
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            )
            // Delete image from path and dismiss notification
            ACTION_DELETE_IMAGE -> deleteImage(
                context, intent.getStringExtra(EXTRA_FILE_LOCATION),
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            )
            // Cancel library update and dismiss notification
            ACTION_CANCEL_LIBRARY_UPDATE -> cancelLibraryUpdate(context)
            ACTION_CANCEL_RESTORE -> cancelRestoreUpdate(context)
            // Open reader activity
            ACTION_OPEN_CHAPTER -> {
                openChapter(
                    context, intent.getLongExtra(EXTRA_MANGA_ID, -1),
                    intent.getLongExtra(EXTRA_CHAPTER_ID, -1)
                )
            }
        }
    }

    /**
     * Dismiss the notification
     *
     * @param notificationId the id of the notification
     */
    private fun dismissNotification(context: Context, notificationId: Int) {
        context.notificationManager.cancel(notificationId)
    }

    /**
     * Called to start share intent to share image
     *
     * @param context context of application
     * @param path path of file
     * @param notificationId id of notification
     */
    private fun shareImage(context: Context, path: String, notificationId: Int) {
        val km = context.getSystemService(Activity.KEYGUARD_SERVICE) as KeyguardManager
        // Create intent
        val intent = Intent(Intent.ACTION_SEND).apply {
            val uri = File(path).getUriCompat(context)
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newRawUri(null, uri)
            type = "image/*"
        }
        // Close Navigation Shade
    }

    /**
     * Starts reader activity
     *
     * @param context context of application
     * @param mangaId id of manga
     * @param chapterId id of chapter
     */
    internal fun openChapter(context: Context, mangaId: Long, chapterId: Long) {
        dismissNotification(context, Notifications.ID_NEW_CHAPTERS)
        val db = DatabaseHelper(context)
        val manga = db.getManga(mangaId).executeAsBlocking()
        val chapter = db.getChapter(chapterId).executeAsBlocking()
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        if (manga != null && chapter != null) {
            val intent = ReaderActivity.newIntent(context, manga, chapter).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } else {
            context.toast(context.getString(R.string.no_next_chapter))
        }
    }

    /**
     * Called to delete image
     *
     * @param path path of file
     * @param notificationId id of notification
     */
    private fun deleteImage(context: Context, path: String, notificationId: Int) {
        // Dismiss notification
        dismissNotification(context, notificationId)

        // Delete file
        val file = File(path)
        file.delete()

        DiskUtil.scanMedia(context, file)
    }

    /**
     * Method called when user wants to stop a library update
     *
     * @param context context of application
     * @param notificationId id of notification
     */
    private fun cancelLibraryUpdate(context: Context) {
        LibraryUpdateService.stop(context)
        Handler().post { dismissNotification(context, Notifications.ID_LIBRARY_PROGRESS) }
    }

    /**
     * Method called when user wants to stop a restore
     *
     * @param context context of application
     * @param notificationId id of notification
     */
    private fun cancelRestoreUpdate(context: Context) {
        BackupRestoreService.stop(context)
        Handler().post { dismissNotification(context, Notifications.ID_RESTORE_PROGRESS) }
    }

    companion object {
        private const val NAME = "NotificationReceiver"

        // Called to launch share intent.
        private const val ACTION_SHARE_IMAGE = "$ID.$NAME.SHARE_IMAGE"

        // Called to delete image.
        private const val ACTION_DELETE_IMAGE = "$ID.$NAME.DELETE_IMAGE"

        // Called to cancel library update.
        private const val ACTION_CANCEL_LIBRARY_UPDATE = "$ID.$NAME.CANCEL_LIBRARY_UPDATE"

        // Called to cancel library update.
        private const val ACTION_CANCEL_RESTORE = "$ID.$NAME.CANCEL_RESTORE"

        // Called to open chapter
        private const val ACTION_OPEN_CHAPTER = "$ID.$NAME.ACTION_OPEN_CHAPTER"

        // Value containing file location.
        private const val EXTRA_FILE_LOCATION = "$ID.$NAME.FILE_LOCATION"

        // Called to resume downloads.
        private const val ACTION_RESUME_DOWNLOADS = "$ID.$NAME.ACTION_RESUME_DOWNLOADS"

        // Called to pause downloads.
        private const val ACTION_PAUSE_DOWNLOADS = "$ID.$NAME.ACTION_PAUSE_DOWNLOADS"

        // Called to clear downloads.
        private const val ACTION_CLEAR_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_DOWNLOADS"

        // Called to notify user shortcut is created.
        private const val ACTION_SHORTCUT_CREATED = "$ID.$NAME.ACTION_SHORTCUT_CREATED"

        // Called to dismiss notification.
        private const val ACTION_DISMISS_NOTIFICATION = "$ID.$NAME.ACTION_DISMISS_NOTIFICATION"

        // Value containing notification id.
        private const val EXTRA_NOTIFICATION_ID = "$ID.$NAME.NOTIFICATION_ID"

        // Value containing group id.
        private const val EXTRA_GROUP_ID = "$ID.$NAME.EXTRA_GROUP_ID"

        // Value containing manga id.
        private const val EXTRA_MANGA_ID = "$ID.$NAME.EXTRA_MANGA_ID"

        // Value containing chapter id.
        private const val EXTRA_CHAPTER_ID = "$ID.$NAME.EXTRA_CHAPTER_ID"

        // Value containing chapter url.
        private const val EXTRA_CHAPTER_URL = "$ID.$NAME.EXTRA_CHAPTER_URL"

        /**
         * Returns a [PendingIntent] that resumes the download of a chapter
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun resumeDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_RESUME_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns [PendingIntent] that pauses the download queue
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun pauseDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_PAUSE_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns a [PendingIntent] that clears the download queue
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun clearDownloadsPendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CLEAR_DOWNLOADS
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        internal fun shortcutCreatedBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SHORTCUT_CREATED
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotificationPendingBroadcast(
            context: Context,
            notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DISMISS_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns [PendingIntent] that starts a service which dismissed the notification
         *
         * @param context context of application
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun dismissNotification(
            context: Context,
            notificationId: Int,
            groupId: Int? =
                null
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val groupKey = context.notificationManager.activeNotifications.find {
                    it.id == notificationId
                }?.groupKey
                if (groupId != null && groupId != 0 && groupKey != null && groupKey.isNotEmpty()) {
                    val notifications = context.notificationManager.activeNotifications.filter {
                        it.groupKey == groupKey
                    }
                    if (notifications.size == 2) {
                        context.notificationManager.cancel(groupId)
                        return
                    }
                }
            }
            context.notificationManager.cancel(notificationId)
        }

        /**
         * Returns [PendingIntent] that starts a service which cancels the notification and starts a share activity
         *
         * @param context context of application
         * @param path location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun shareImagePendingBroadcast(
            context: Context,
            path: String,
            notificationId: Int
        ): PendingIntent {
            // val shareIntent = ShareStartingActivity.newIntent(context, path)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                val uri = File(path).getUriCompat(context)
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                clipData = ClipData.newRawUri(null, uri)
                type = "image/*"
            }
            // val shareIntent2 = Intent.createChooser(shareIntent, context.getString(R.string.action_share))
            return PendingIntent.getActivity(
                context, 0, shareIntent, PendingIntent
                    .FLAG_CANCEL_CURRENT
            )
        }

        /**
         * Returns [PendingIntent] that starts a service which removes an image from disk
         *
         * @param context context of application
         * @param path location path of file
         * @param notificationId id of notification
         * @return [PendingIntent]
         */
        internal fun deleteImagePendingBroadcast(
            context: Context,
            path: String,
            notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DELETE_IMAGE
                putExtra(EXTRA_FILE_LOCATION, path)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns [PendingIntent] that starts a reader activity containing chapter.
         *
         * @param context context of application
         * @param manga manga of chapter
         * @param chapter chapter that needs to be opened
         */
        internal fun openChapterPendingActivity(
            context: Context,
            manga: Manga,
            chapter:
            Chapter
        ): PendingIntent {
            val newIntent = ReaderActivity.newIntent(context, manga, chapter)
            return PendingIntent.getActivity(
                context, manga.id.hashCode(), newIntent, PendingIntent
                    .FLAG_UPDATE_CURRENT
            )
        }

        /**
         * Returns [PendingIntent] that opens the manga info controller.
         *
         * @param context context of application
         * @param manga manga of chapter
         */
        internal fun openChapterPendingActivity(context: Context, manga: Manga, groupId: Int):
            PendingIntent {
            val newIntent =
                Intent(context, MainActivity::class.java).setAction(MainActivity.SHORTCUT_MANGA)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MangaController.MANGA_EXTRA, manga.id)
                    .putExtra("notificationId", manga.id.hashCode())
                    .putExtra("groupId", groupId)
            return PendingIntent.getActivity(
                context, manga.id.hashCode(), newIntent, PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /**Returns the PendingIntent that will open the error log in an external text viewer
         *
         */
        internal fun openFileExplorerPendingActivity(context: Context, uri: Uri): PendingIntent {
            val toLaunch = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            return PendingIntent.getActivity(context, 0, toLaunch, 0)
        }

        /**
         * Returns [PendingIntent] that starts a service which stops the library update
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun cancelLibraryUpdatePendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_LIBRARY_UPDATE
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns [PendingIntent] that starts a service which stops the restore service
         *
         * @param context context of application
         * @return [PendingIntent]
         */
        internal fun cancelRestorePendingBroadcast(context: Context): PendingIntent {
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CANCEL_RESTORE
            }
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
