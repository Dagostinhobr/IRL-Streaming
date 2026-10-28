package app.streamlink.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.streamlink.MainActivity
import app.streamlink.R
import app.streamlink.i18n.L10n

/**
 * PT: Foreground service que mantém o processo vivo enquanto a transmissão está ativa.
 * PT: O encoder roda na Activity/ViewModel; o serviço cuida da notificação persistente
 * PT: e da ação "Encerrar".
 *
 * EN: Foreground service that keeps the process alive while the stream is active.
 * EN: The encoder runs in the Activity/ViewModel; the service handles the persistent
 * EN: notification and the "End" action.
 */
class StreamService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, REQUEST_OPEN, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_STOP_STREAM
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            this, REQUEST_STOP, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle(L10n.t("Transmissão ao vivo", "Live stream"))
            .setContentText(L10n.t("IRL Streaming está no ar", "IRL Streaming is live"))
            .setContentIntent(openPendingIntent)
            .addAction(0, L10n.t("Encerrar", "End"), stopPendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            L10n.t("Transmissão ao vivo", "Live stream"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = L10n.t(
                "Mantém a transmissão ativa em segundo plano",
                "Keeps the stream active in the background"
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "app.streamlink.action.STOP"
        private const val CHANNEL_ID = "streamlink_live"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 10
        private const val REQUEST_STOP = 11
    }
}
