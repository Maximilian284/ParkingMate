package com.example.parkingmate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.example.parkingmate.data.ParkingSession

object AlarmHelper {

    fun scheduleFixedTicketAlarms(context: Context, session: ParkingSession, vehicleName: String) {
        // 1. IL BUG ERA QUI! Ora accettiamo "Già Pagato", "Costo Fisso" o "Fixed"
        if (session.endTime == null) return
        if (session.type != "Già Pagato" && session.type != "Costo Fisso" && session.type != "Fixed") return

        val prefs = context.getSharedPreferences("ParkingMatePrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("fixed_enabled", true)

        if (!isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val minutesBefore = prefs.getInt("fixed_minutes", 15)
        val expiryTime = session.endTime
        val warningTime = expiryTime - (minutesBefore * 60 * 1000)

        // 2. PIANO B PER I PERMESSI: Se Android 14+ vieta gli allarmi esatti, usiamo un allarme standard
        val canSetExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        // --- ALLARME AVVISO PREVENTIVO ---
        if (warningTime > System.currentTimeMillis()) {
            val warningIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("VEHICLE_NAME", vehicleName)
                putExtra("IS_EXPIRY", false)
                putExtra("PARKING_ID", session.id)
            }
            val warningPendingIntent = PendingIntent.getBroadcast(
                context, session.id * 10, warningIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (canSetExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningTime, warningPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warningTime, warningPendingIntent)
            }
        }

        // --- ALLARME SCADENZA VERA E PROPRIA ---
        if (expiryTime > System.currentTimeMillis()) {
            val expiryIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("VEHICLE_NAME", vehicleName)
                putExtra("IS_EXPIRY", true)
                putExtra("PARKING_ID", session.id)
            }
            val expiryPendingIntent = PendingIntent.getBroadcast(
                context, session.id * 10 + 1, expiryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (canSetExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiryTime, expiryPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiryTime, expiryPendingIntent)
            }

            // 3. CONFERMA VISIVA PER VOI!
            Toast.makeText(context, "Sveglia impostata! Il telefono suonerà all'orario previsto.", Toast.LENGTH_LONG).show()
        } else {
            // Se mettete un orario nel passato, ve lo dice!
            Toast.makeText(context, "Attenzione: la scadenza inserita è già passata!", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelAlarms(context: Context, sessionId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val warningIntent = Intent(context, AlarmReceiver::class.java)
        val warningPendingIntent = PendingIntent.getBroadcast(context, sessionId * 10, warningIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val expiryIntent = Intent(context, AlarmReceiver::class.java)
        val expiryPendingIntent = PendingIntent.getBroadcast(context, sessionId * 10 + 1, expiryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(warningPendingIntent)
        alarmManager.cancel(expiryPendingIntent)
    }
}