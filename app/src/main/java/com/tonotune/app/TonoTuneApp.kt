package com.tonotune.app

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tonotune.app.ui.MainActivity

class TonoTuneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    companion object {
        const val CHANNEL_ENGINE = "tono_engine"
        const val CHANNEL_ALERTS = "tono_alerts"
        private const val ACTIVATION_ID = 0x70A1

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ENGINE, context.getString(R.string.channel_engine), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_engine_desc)
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_alerts_desc)
                },
            )
        }

        /**
         * Android 12+ may refuse to start a foreground service from the background (if the app
         * is not exempt from battery optimisation). Offer a one-tap activation instead.
         */
        fun postActivationPrompt(context: Context, deviceName: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val intent = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_tono)
                .setContentTitle(context.getString(R.string.activation_title, deviceName))
                .setContentText(context.getString(R.string.activation_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.activation_text)))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.tono_accent))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(ACTIVATION_ID, notification) }
        }
    }
}
