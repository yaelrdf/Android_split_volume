package dev.yaelrdf.splitvolume

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.yaelrdf.splitvolume.ui.MainActivity
import dev.yaelrdf.splitvolume.ui.QuickControlsActivity

object Notifications {
    private const val CHANNEL_ID = "split_volume"
    const val SESSION_ID = 1
    const val PROMPT_ID = 2
    const val STEP = 5

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Volume controls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "L/R volume controls while your headphones are connected"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun status(context: Context, title: String, text: String): Notification =
        base(context, openActivity(context, MainActivity::class.java))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

    /** Notifications can't host real sliders, so this shows a level bar per side with −/+ buttons. */
    fun controls(context: Context, profile: Profile): Notification {
        val small = RemoteViews(context.packageName, R.layout.notification_small).apply { bind(context, profile) }
        val big = RemoteViews(context.packageName, R.layout.notification_big).apply {
            bind(context, profile)
            setProgressBar(R.id.l_bar, 100, profile.left, false)
            setProgressBar(R.id.r_bar, 100, profile.right, false)
            setOnClickPendingIntent(R.id.swap, action(context, NotificationActionReceiver.ACTION_SWAP, null, 0))
        }
        return base(context, openActivity(context, QuickControlsActivity::class.java))
            .setContentTitle("L ${profile.left}% · R ${profile.right}%")
            .setSubText(profile.deviceName)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .build()
    }

    /** Shown when Android blocked starting the session from the background; tapping it starts it from the foreground. */
    fun activatePrompt(context: Context): Notification =
        base(context, openActivity(context, QuickControlsActivity::class.java))
            .setContentTitle("Headphones connected")
            .setContentText("Tap to turn on L/R volume")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    fun show(context: Context, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun base(context: Context, contentIntent: PendingIntent) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    private fun RemoteViews.bind(context: Context, profile: Profile) {
        setTextViewText(R.id.l_value, "${profile.left}%")
        setTextViewText(R.id.r_value, "${profile.right}%")
        setOnClickPendingIntent(R.id.l_minus, action(context, NotificationActionReceiver.ACTION_ADJUST, Side.L, -STEP))
        setOnClickPendingIntent(R.id.l_plus, action(context, NotificationActionReceiver.ACTION_ADJUST, Side.L, STEP))
        setOnClickPendingIntent(R.id.r_minus, action(context, NotificationActionReceiver.ACTION_ADJUST, Side.R, -STEP))
        setOnClickPendingIntent(R.id.r_plus, action(context, NotificationActionReceiver.ACTION_ADJUST, Side.R, STEP))
    }

    private fun action(context: Context, action: String, side: Side?, delta: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_SIDE, side?.name)
            .putExtra(NotificationActionReceiver.EXTRA_DELTA, delta)
        // Distinct request codes, or the four −/+ buttons would collapse into one PendingIntent.
        val requestCode = when {
            side == null -> 0
            else -> 1 + side.ordinal * 2 + if (delta > 0) 1 else 0
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openActivity(context: Context, activity: Class<*>): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
