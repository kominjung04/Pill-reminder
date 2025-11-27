package com.example.medschedule

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val meals = listOf("아침", "점심", "저녁")
    private val prefsName = "meal_prefs"

    private lateinit var containerMeals: LinearLayout
    private lateinit var btnSave: Button
    private val mealHours = mutableMapOf<String, Int>()
    private val mealMinutes = mutableMapOf<String, Int>()
    private val mealActive = mutableMapOf<String, Boolean>()

    private val notifyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "알림 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerMeals = findViewById(R.id.container_meals)
        btnSave = findViewById(R.id.btn_save)

        mealHours["아침"] = 10
        mealMinutes["아침"] = 10
        mealActive["아침"] = true
        mealHours["점심"] = 17
        mealMinutes["점심"] = 0
        mealActive["점심"] = true
        mealHours["저녁"] = 21
        mealMinutes["저녁"] = 0
        mealActive["저녁"] = true

        loadPreferences()
        buildMealViews()

        btnSave.setOnClickListener {
            saveAllPreferences()
            Toast.makeText(this, "저장되었습니다!", Toast.LENGTH_SHORT).show()
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun buildMealViews() {
        containerMeals.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (meal in meals) {
            val item = inflater.inflate(R.layout.item_meal, containerMeals, false)
            val tvMealName = item.findViewById<TextView>(R.id.tvMealName)
            val ivToggle = item.findViewById<ImageView>(R.id.ivToggle)
            val tvTime = item.findViewById<TextView>(R.id.tvTime)
            val btnChange = item.findViewById<Button>(R.id.btnChangeTime)

            tvMealName.text = meal
            updateMealTimeText(tvTime, meal)
            updateMealToggleIcon(ivToggle, meal)

            ivToggle.setOnClickListener {
                val newVal = !(mealActive[meal] ?: false)
                mealActive[meal] = newVal
                updateMealToggleIcon(ivToggle, meal)
                savePreferenceForMeal(meal)
                if (newVal) scheduleMealAlarm(meal) else cancelMealAlarm(meal)
            }

            btnChange.setOnClickListener {
                showTimePickerDialog(meal) { h, m ->
                    mealHours[meal] = h
                    mealMinutes[meal] = m
                    updateMealTimeText(tvTime, meal)
                    savePreferenceForMeal(meal)
                    if (mealActive[meal] == true) scheduleMealAlarm(meal)
                }
            }

            containerMeals.addView(item)
        }
    }

    private fun updateMealTimeText(tv: TextView, meal: String) {
        val hour = mealHours[meal] ?: 0
        val minute = mealMinutes[meal] ?: 0
        val ampm = if (hour < 12) "오전" else "오후"
        val hourOfPeriod = if (hour % 12 == 0) 12 else hour % 12
        tv.text = "$ampm ${hourOfPeriod}시 ${minute.toString().padStart(2,'0')}분"
    }

    private fun updateMealToggleIcon(iv: ImageView, meal: String) {
        val active = mealActive[meal] ?: false
        if (active) iv.setImageResource(android.R.drawable.checkbox_on_background)
        else iv.setImageResource(android.R.drawable.checkbox_off_background)
    }

    private fun showTimePickerDialog(meal: String, onSave: (Int, Int) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val npHour = dialogView.findViewById<NumberPicker>(R.id.npHour)
        val npMinute = dialogView.findViewById<NumberPicker>(R.id.npMinute)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveDialog)

        npHour.minValue = 0
        npHour.maxValue = 23
        npHour.value = mealHours[meal] ?: 0

        val minuteValues = (0..59).map { it.toString().padStart(2,'0') }.toTypedArray()
        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.displayedValues = minuteValues
        npMinute.value = mealMinutes[meal] ?: 0

        val alert = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        btnSave.setOnClickListener {
            onSave(npHour.value, npMinute.value)
            alert.dismiss()
        }
        alert.show()
    }

    private fun savePreferenceForMeal(meal: String) {
        val sp = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        sp.edit()
            .putInt("${meal}_hour", mealHours[meal] ?: 0)
            .putInt("${meal}_minute", mealMinutes[meal] ?: 0)
            .putBoolean("${meal}_active", mealActive[meal] ?: true)
            .apply()
    }

    private fun saveAllPreferences() {
        for (meal in meals) {
            savePreferenceForMeal(meal)
            if (mealActive[meal] == true) scheduleMealAlarm(meal)
            else cancelMealAlarm(meal)
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        for (meal in meals) {
            mealHours[meal] = sp.getInt("${meal}_hour", mealHours[meal] ?: 0)
            mealMinutes[meal] = sp.getInt("${meal}_minute", mealMinutes[meal] ?: 0)
            mealActive[meal] = sp.getBoolean("${meal}_active", mealActive[meal] ?: true)
            if (mealActive[meal] == true) scheduleMealAlarm(meal)
        }
    }

    private fun scheduleMealAlarm(meal: String) {
        val hour = mealHours[meal] ?: return
        val minute = mealMinutes[meal] ?: return

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("meal", meal)
            action = meal
        }

        val requestCode = when (meal) {
            "아침" -> 1001
            "점심" -> 1002
            "저녁" -> 1003
            else -> 9999
        }

        val pending = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }

        Log.d("AlarmDebug", "$meal 알람 등록: ${calendar.time}")
    }



    private fun cancelMealAlarm(meal: String) {
        val intent = Intent(this, AlarmReceiver::class.java)
        val requestCode = when (meal) {
            "아침" -> 1001
            "점심" -> 1002
            "저녁" -> 1003
            else -> 9999
        }

        val pending = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        pending.cancel()
    }

}
