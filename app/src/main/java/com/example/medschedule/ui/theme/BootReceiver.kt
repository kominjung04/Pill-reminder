package com.example.medschedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "기기 재부팅 감지, 알람 재등록")

            val sp = context.getSharedPreferences("meal_prefs", Context.MODE_PRIVATE)
            val meals = listOf("아침", "점심", "저녁")

            for (meal in meals) {
                val hour = sp.getInt("${meal}_hour", -1)
                val minute = sp.getInt("${meal}_minute", -1)
                val active = sp.getBoolean("${meal}_active", false)

                if (hour >= 0 && minute >= 0 && active) {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val intentAlarm = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("meal", meal)
                        action = meal //
                    }

                    val requestCode = when (meal) {
                        "아침" -> 1001
                        "점심" -> 1002
                        "저녁" -> 1003
                        else -> 9999
                    }

                    val pending = android.app.PendingIntent.getBroadcast(
                        context,
                        requestCode, // 개별 알람 구분
                        intentAlarm,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                                    android.app.PendingIntent.FLAG_IMMUTABLE else 0
                    )

                    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
                    } else {
                        am.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
                    }

                    Log.d("BootReceiver", "$meal 알람 재등록: ${calendar.time}")
                }
            }
        }
    }
}
