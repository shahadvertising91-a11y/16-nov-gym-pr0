package com.forge.bar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.ArrayDeque
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

/**
 * Foreground service (type "health") that owns the hardware step counter and keeps an
 * ongoing notification styled like a fitness tracker card:
 *   collapsed: "6,789 steps"            "229 kcal · 4.21 km"   + slim goal bar
 *   expanded : big step count, goal ring with %, and KCAL / KM / TO GO
 * The layouts live in native/res (copied into the app by scripts/patch_android.py). If they are
 * missing, the plain system notification with a progress bar is used instead.
 * Must be started while the app is in the foreground, after ACTIVITY_RECOGNITION has been granted.
 */
class StepService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL = "forge_bar_v2"        // v2: default importance (shown in the main section, silent)
        const val OLD_CHANNEL = "forge_bar"
        const val NOTIF_ID = 4101
        const val ACTION_STOP = "com.forge.bar.STOP"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_KPS = "kps"
        const val EXTRA_STRIDE = "stride"
        @Volatile var instance: StepService? = null
    }

    private lateinit var store: StepStore
    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null          // TYPE_STEP_COUNTER: accurate, cumulative
    private var detector: Sensor? = null        // TYPE_STEP_DETECTOR: one event per step, instant
    private var steps = 0L
    private var counterSteps = 0L
    private val pendingTs = ArrayDeque<Long>()  // detector steps the counter has not reported yet
    private var lastNotif = 0L
    private var refreshQueued = false
    private val handler = Handler(Looper.getMainLooper())
    private val midnight = Runnable { onNewDay() }

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { onNewDay() }   // DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = StepStore(this)
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        detector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        createChannel()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dateReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopFromApp(); return START_NOT_STICKY }
        intent?.let {
            if (it.hasExtra(EXTRA_GOAL)) store.goal = it.getIntExtra(EXTRA_GOAL, store.goal)
            if (it.hasExtra(EXTRA_KPS)) store.kps = it.getFloatExtra(EXTRA_KPS, store.kps)
            if (it.hasExtra(EXTRA_STRIDE)) store.stride = it.getFloatExtra(EXTRA_STRIDE, store.stride)
        }
        counterSteps = store.todaySteps()
        pendingTs.clear()
        steps = counterSteps

        // Android 12+: FGS can only be started from the foreground; Android 14+: the type is mandatory
        // and ACTIVITY_RECOGNITION must already be granted (otherwise SecurityException).
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else
                startForeground(NOTIF_ID, n)
        } catch (e: Exception) {            // ForegroundServiceStartNotAllowedException / SecurityException
            stopSelf(); return START_NOT_STICKY
        }

        store.running = true
        sm.unregisterListener(this)
        // report immediately (no batching): maxReportLatency = 0
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        detector?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        scheduleMidnight()
        ForgeBarPlugin.emitState(true)
        ForgeBarPlugin.emitSteps(steps, store.today())
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            // instant: +1 on the very step
            pendingTs.addLast(e.timestamp)
            while (pendingTs.size > 400) pendingTs.removeFirst()
        } else {
            // accurate: the hardware counter is the truth; drop detector steps it already includes
            counterSteps = store.onCounter(e.values[0].toLong())
            while (pendingTs.isNotEmpty() && pendingTs.first() <= e.timestamp) pendingTs.removeFirst()
        }
        steps = counterSteps + pendingTs.size
        refreshNotification()
        ForgeBarPlugin.emitSteps(steps, store.today())
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun onNewDay() {
        store.rollover()
        counterSteps = store.todaySteps()
        pendingTs.clear()
        steps = counterSteps
        refreshNotification()
        ForgeBarPlugin.emitSteps(steps, store.today())
        scheduleMidnight()
    }

    private fun scheduleMidnight() {
        handler.removeCallbacks(midnight)
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 2); set(Calendar.MILLISECOND, 0)
        }
        handler.postDelayed(midnight, c.timeInMillis - System.currentTimeMillis())
    }

    /** goal / kcal-per-step changed from JS: redraw the notification right away */
    fun refresh() { steps = counterSteps + pendingTs.size; doRefresh() }

    fun stopFromApp() {
        store.running = false
        try { sm.unregisterListener(this) } catch (_: Exception) {}
        handler.removeCallbacks(midnight)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        ForgeBarPlugin.emitState(false)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        try { sm.unregisterListener(this) } catch (_: Exception) {}
        try { unregisterReceiver(dateReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(midnight)
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    // ---------------- notification ----------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) }      // old channel was LOW importance ("Silent" section)
        // DEFAULT importance => listed with the normal notifications at the top, not under "Silent".
        // No sound, no vibration, and setOnlyAlertOnce below => it never makes noise.
        val ch = NotificationChannel(CHANNEL, "Steps & activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Live steps, calories and distance"
            setShowBadge(false); enableVibration(false); setSound(null, null); enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    private fun smallIcon(): Int {
        val id = resources.getIdentifier("ic_stat_forge", "drawable", packageName)
        return if (id != 0) id else applicationInfo.icon
    }

    private fun buildNotification(): Notification {
        val goal = store.goal
        val loc = Locale.getDefault()
        val kcal = Math.round(steps * store.kps)
        val km = steps * store.stride / 1000.0
        val pct = minOf(100, (steps * 100 / maxOf(1, goal)).toInt())
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, StepService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val line1 = String.format(loc, "%,d kcal  •  %.2f km", kcal, km)
        val line2 = String.format(loc, "%d%% of your %,d step goal", pct, goal)
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(smallIcon())
            .setColor(0xFF9BD61F.toInt())
            .setContentTitle(String.format(loc, "%,d steps today", steps))
            .setContentText(line2)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Pause", stop)

        val views = try { cardViews(goal, kcal, km, pct, loc) } catch (e: Exception) { null }
        if (views != null) {
            // fitness-card look: our own layout inside the standard notification frame
            b.setSubText("Today")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views.first)
                .setCustomBigContentView(views.second)
        } else {
            // fallback: plain system notification
            b.setSubText("Forge Gym")
                .setStyle(NotificationCompat.BigTextStyle().bigText(line1 + "\n" + line2))
                .setProgress(goal, minOf(steps, goal.toLong()).toInt(), false)
        }
        return b.build()
    }

    private fun rid(name: String, type: String): Int = resources.getIdentifier(name, type, packageName)

    /** collapsed + expanded custom layouts, or null when the layout resources are not in the app */
    private fun cardViews(goal: Int, kcal: Int, km: Double, pct: Int, loc: Locale): Pair<RemoteViews, RemoteViews>? {
        val smallId = rid("forge_notif_small", "layout")
        val bigId = rid("forge_notif_big", "layout")
        val idSteps = rid("fn_steps", "id"); val idSide = rid("fn_side", "id"); val idBar = rid("fn_bar", "id")
        val idGoal = rid("fn_goal", "id"); val idRing = rid("fn_ring", "id"); val idPct = rid("fn_pct", "id")
        val idKcal = rid("fn_kcal", "id"); val idKm = rid("fn_km", "id")
        val idLeft = rid("fn_left", "id"); val idLeftLabel = rid("fn_left_label", "id")
        if (smallId == 0 || bigId == 0 || idSteps == 0 || idSide == 0 || idBar == 0 || idGoal == 0 || idRing == 0 ||
            idPct == 0 || idKcal == 0 || idKm == 0 || idLeft == 0 || idLeftLabel == 0) return null

        val stepsTxt = String.format(loc, "%,d", steps)
        val kcalTxt = String.format(loc, "%,d", kcal)
        val kmTxt = String.format(loc, "%.2f", km)
        val realPct = (steps * 100 / maxOf(1, goal)).toInt()          // may go past 100
        val left = goal.toLong() - steps

        val small = RemoteViews(packageName, smallId)
        small.setTextViewText(idSteps, stepsTxt)
        small.setTextViewText(idSide, "$kcalTxt kcal  •  $kmTxt km")
        small.setProgressBar(idBar, 100, pct, false)

        val big = RemoteViews(packageName, bigId)
        big.setTextViewText(idSteps, stepsTxt)
        big.setTextViewText(
            idGoal,
            if (left > 0) String.format(loc, "Goal %,d", goal) else String.format(loc, "Goal %,d reached", goal)
        )
        big.setProgressBar(idRing, 100, pct, false)
        big.setTextViewText(idPct, "$realPct%")
        big.setTextViewText(idKcal, kcalTxt)
        big.setTextViewText(idKm, kmTxt)
        if (left > 0) {
            big.setTextViewText(idLeft, String.format(loc, "%,d", left))
            big.setTextViewText(idLeftLabel, "TO GO")
        } else {
            big.setTextViewText(idLeft, String.format(loc, "+%,d", -left))
            big.setTextViewText(idLeftLabel, "OVER GOAL")
        }
        return Pair(small, big)
    }

    /** at most one redraw per second (Android rate-limits notification updates), never loses the last value */
    private fun refreshNotification() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotif >= 1000) { doRefresh(); return }
        if (!refreshQueued) {
            refreshQueued = true
            handler.postDelayed({ refreshQueued = false; doRefresh() }, 1000 - (now - lastNotif))
        }
    }

    private fun doRefresh() {
        lastNotif = SystemClock.elapsedRealtime()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
