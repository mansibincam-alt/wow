package com.dxm.aiconversation

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class ChatService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wl: PowerManager.WakeLock? = null

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "dxm")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("DXM AI Conversation").setContentText(text)
            .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dxm", "AI Chat", NotificationManager.IMPORTANCE_LOW))
        val n = notif("চ্যাট চলছে…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        if (job == null) {
            wl = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dxm:chat").apply { acquire() }
            job = scope.launch { Engine.run(applicationContext) }
            scope.launch {
                Repo.msgs.collect { l ->
                    l.lastOrNull()?.let { nm.notify(1, notif("${it.who.bn}: ${it.text.ifBlank { it.note }}")) }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel(); job = null
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null
}
