package com.simpletools.area.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.simpletools.area.MainActivity
import com.simpletools.area.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Service de premier plan (type "location") : maintient l'enregistrement quand l'écran est verrouillé.
 * La localisation en arrière-plan n'est PAS demandée : le service démarre depuis l'interface au premier plan.
 */
class GpsTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

        val source = AndroidLocationSource(this)
        if (job == null) {
            job = scope.launch {
                source.events()
                    .catch { stopSelfSafely() }
                    .collect { e ->
                        when (e) {
                            is LocationEvent.Fix -> GpsSession.recorder.onFix(e.fix)
                            is LocationEvent.ProviderEnabled -> GpsSession.recorder.setProviderEnabled(e.enabled)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSelfSafely() {
        job?.cancel(); job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gps_tracking"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.simpletools.area.STOP_TRACKING"

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, GpsTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GpsTrackingService::class.java))
        }
    }
}
