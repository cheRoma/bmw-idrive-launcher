package online.k73.bmwlauncher.music

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant "Notification access", which in turn lets
 * MediaSessionManager.getActiveSessions() hand us other apps' MediaControllers.
 * We do not process notifications here.
 */
class MediaNotificationListener : NotificationListenerService()
