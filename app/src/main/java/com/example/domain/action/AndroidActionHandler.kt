package com.example.domain.action

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.alarm.AkritiAlarmScheduler
import com.example.data.model.ActionType
import com.example.data.model.ContactMatch
import com.example.data.model.IntentCommand
import com.example.data.model.PendingAction
import com.example.data.repository.VoiceNotesRepository
import com.example.voice.VoiceRecognitionCorrector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ActionResult {
    data class Handled(val message: String) : ActionResult()
    data class ExecutedWithInfo(val info: String) : ActionResult()
    data class Failed(val error: String) : ActionResult()
    data class RequiresConfirmation(
        val prompt: String,
        val pendingAction: PendingAction
    ) : ActionResult()
    object Ignored : ActionResult()
}

class AndroidActionHandler(
    private val context: Context,
    private val notesRepository: VoiceNotesRepository? = null,
    private val onShowNotes: (() -> Unit)? = null,
    private val onShowScreenTime: (() -> Unit)? = null
) {

    private val alarmScheduler = AkritiAlarmScheduler(context)

    companion object {
        private var isTorchOn = false
    }

    fun isFlashlightActive(): Boolean = isTorchOn

    fun getBatteryStatus(): Pair<Int, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging ?: false
        return Pair(level, isCharging)
    }

    /**
     * Executes safe Android system intents. Never executes arbitrary shell commands or code.
     */
    fun handleAction(intent: IntentCommand): ActionResult {
        return when (intent.action) {
            ActionType.WHATSAPP_OPEN -> openWhatsApp()
            ActionType.WHATSAPP_MESSAGE -> prepareWhatsAppMessage(intent)
            ActionType.YOUTUBE_OPEN -> openYouTube()
            ActionType.YOUTUBE_SEARCH -> searchYouTube(intent.target ?: intent.rawQuery)
            ActionType.CALL_PHONE -> preparePhoneCall(intent.target)
            ActionType.BATTERY_INFO -> getBatteryInfo()
            ActionType.DATE_TIME -> getDateTimeInfo()
            ActionType.DEVICE_INFO -> getDeviceInfo()
            ActionType.OPEN_APP -> openApplication(intent.target)
            ActionType.OPEN_SETTINGS -> openSettings(intent.target)
            ActionType.SEARCH_WEB -> searchWeb(intent.target ?: intent.rawQuery)
            ActionType.LAUNCH_URL -> launchUrl(intent.target)
            ActionType.SET_TIMER -> setTimer(intent.target, intent.parameters)
            ActionType.SET_ALARM -> setAlarm(intent.target, intent.parameters, intent.rawQuery)
            ActionType.CANCEL_ALARM -> cancelAlarm(intent.target, intent.parameters, intent.rawQuery)
            ActionType.SET_REMINDER -> setReminder(intent.target, intent.rawQuery)
            ActionType.TAKE_NOTE -> saveVoiceNote(intent.target ?: intent.rawQuery)
            ActionType.SHOW_NOTES -> showNotesList()
            ActionType.DIAL_PHONE -> dialPhone(intent.target)
            ActionType.SEND_MESSAGE -> sendMessage(intent.target, intent.rawQuery)
            ActionType.TOGGLE_FLASHLIGHT -> toggleFlashlight(intent.target)
            ActionType.SHARE_CONTENT -> shareContent(intent.target ?: intent.rawQuery)
            ActionType.SCREEN_TIME -> showScreenTime()
            ActionType.NONE -> ActionResult.Ignored
        }
    }

    private fun showScreenTime(): ActionResult {
        onShowScreenTime?.invoke()
        val manager = com.example.domain.screentime.ScreenTimeManager(context)
        val summary = manager.getScreenTimeSummary()
        return if (summary.hasPermission) {
            ActionResult.Handled("Aaj ka total screen time ${summary.formattedToday} hai.")
        } else {
            ActionResult.Handled("Screen time dekhne ke liye Usage Access permission chahiye.")
        }
    }

    // --- WhatsApp Integration ---
    fun openWhatsApp(): ActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ActionResult.Handled("WhatsApp open kar diya hai.")
            } else {
                ActionResult.Failed("Aapke phone mein WhatsApp install nahi hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("WhatsApp open karne mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    private fun prepareWhatsAppMessage(intent: IntentCommand): ActionResult {
        val recipient = intent.parameters["recipient"] ?: intent.target
        val message = intent.parameters["message"]

        if (recipient.isNullOrBlank()) {
            return ActionResult.Failed("WhatsApp par kise message bhejna hai, kripya batayein.")
        }

        if (message.isNullOrBlank()) {
            return ActionResult.Failed("$recipient ko WhatsApp par kya message bhejna hai, kripya batayein.")
        }

        val digitsCount = recipient.count { it.isDigit() }
        val hasLetters = recipient.any { it.isLetter() }

        if (!hasLetters && digitsCount >= 7) {
            return openWhatsAppChatWithMessage(
                phoneNumber = recipient,
                contactName = recipient,
                messageText = message
            )
        }

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContacts) {
            return ActionResult.Failed("Contact se WhatsApp message bhejne ke liye Contacts permission ki zaroorat hai.")
        }

        val matches = findContactsByName(recipient)
        return when {
            matches.isEmpty() -> {
                ActionResult.Failed("Mujhe '$recipient' naam ka koi contact nahi mila. Kripya phone number batayein.")
            }
            matches.size > 1 -> {
                val list = matches.take(3).mapIndexed { idx, c -> "${idx + 1}. ${c.name} (${c.number})" }.joinToString(", ")
                ActionResult.RequiresConfirmation(
                    prompt = "Mujhe '$recipient' ke liye multiple contacts mile: $list. Kise WhatsApp message bhejna hai?",
                    pendingAction = PendingAction.DisambiguateContact(
                        contacts = matches,
                        targetAction = ActionType.WHATSAPP_MESSAGE,
                        pendingMessage = message
                    )
                )
            }
            else -> {
                val match = matches.first()
                openWhatsAppChatWithMessage(
                    phoneNumber = match.number,
                    contactName = match.name,
                    messageText = message
                )
            }
        }
    }

    fun openWhatsAppChatWithMessage(phoneNumber: String, contactName: String, messageText: String): ActionResult {
        return try {
            val rawNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            val cleanedNumber = if (!rawNumber.startsWith("+") && rawNumber.length == 10) {
                "91$rawNumber"
            } else {
                rawNumber.removePrefix("+")
            }

            val encodedMessage = Uri.encode(messageText)
            val uri = Uri.parse("https://wa.me/$cleanedNumber?text=$encodedMessage")

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, messageText)
                putExtra("text", messageText)
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ActionResult.Handled("$contactName ke liye WhatsApp chat open kar di hai.")
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    putExtra("text", messageText)
                    putExtra("sms_body", messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ActionResult.Handled("$contactName ke liye WhatsApp link open kar diya hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("WhatsApp message bhejne mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun executeConfirmedWhatsApp(pending: PendingAction.SendWhatsAppMessage): ActionResult {
        return openWhatsAppChatWithMessage(
            phoneNumber = pending.phoneNumber,
            contactName = pending.contactName,
            messageText = pending.messageText
        )
    }

    // --- YouTube Integration ---
    fun openYouTube(): ActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ActionResult.Handled("YouTube open kar diya hai.")
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ActionResult.Handled("YouTube browser mein open ho raha hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("YouTube open karne mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun searchYouTube(query: String?): ActionResult {
        val q = query?.trim().orEmpty()
        if (q.isBlank()) return openYouTube()

        return try {
            val pm = context.packageManager
            val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (ytIntent.resolveActivity(pm) != null) {
                context.startActivity(ytIntent)
                ActionResult.Handled("YouTube par '$q' search kar diya hai.")
            } else {
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ActionResult.Handled("YouTube par '$q' search kiya ja raha hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("YouTube search karne mein dikkat aayi.")
        }
    }

    // --- Phone Calling ---
    private fun preparePhoneCall(target: String?): ActionResult {
        val cleaned = target?.trim().orEmpty()
        if (cleaned.isBlank()) {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            return ActionResult.Handled("Phone dialer open kar diya hai.")
        }

        val digitsCount = cleaned.count { it.isDigit() }
        val hasLetters = cleaned.any { it.isLetter() }

        if (!hasLetters && digitsCount >= 3) {
            return initiateCall(contactName = cleaned, phoneNumber = cleaned)
        }

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadContacts) {
            return ActionResult.Failed("Contact se call karne ke liye Contacts permission ki zaroorat hai.")
        }

        val matches = findContactsByName(cleaned)
        return when {
            matches.isEmpty() -> {
                ActionResult.Handled("Mujhe '$cleaned' naam ka koi contact nahi mila.")
            }
            matches.size > 1 -> {
                val list = matches.take(3).mapIndexed { idx, c -> "${idx + 1}. ${c.name} (${c.number})" }.joinToString(", ")
                ActionResult.RequiresConfirmation(
                    prompt = "Mujhe '$cleaned' ke liye multiple contacts mile: $list. Kise call karoon?",
                    pendingAction = PendingAction.DisambiguateContact(
                        contacts = matches,
                        targetAction = ActionType.CALL_PHONE
                    )
                )
            }
            else -> {
                val match = matches.first()
                initiateCall(contactName = match.name, phoneNumber = match.number)
            }
        }
    }

    fun initiateCall(contactName: String, phoneNumber: String): ActionResult {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            if (hasCallPermission) {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ActionResult.Handled("$contactName ko call lagayi ja rahi hai.")
            } else {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ActionResult.Failed("Direct call lagane ke liye Phone permission ki zaroorat hai. Dialer open kar diya hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("Call lagane mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun executeConfirmedCall(pending: PendingAction.MakePhoneCall): ActionResult {
        return initiateCall(pending.contactName, pending.phoneNumber)
    }

    // --- Voice Notes ---
    private fun saveVoiceNote(rawNote: String?): ActionResult {
        val content = rawNote?.trim().orEmpty()
        if (content.isBlank()) {
            return ActionResult.Failed("Note content khali tha, save nahi kiya gaya.")
        }
        val previewTitle = if (content.length > 28) content.take(28) + "..." else content
        notesRepository?.addNote(title = previewTitle, content = content)
        return ActionResult.ExecutedWithInfo("Note save kar liya gaya hai: \"$previewTitle\"")
    }

    private fun showNotesList(): ActionResult {
        onShowNotes?.invoke()
        return ActionResult.ExecutedWithInfo("Aapke saved notes screen par dikha rahi hoon.")
    }

    // --- Battery & Device Info ---
    private fun getBatteryInfo(): ActionResult {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging ?: false

            if (level != -1) {
                val status = if (isCharging) "charging ho raha hai ⚡" else "charging nahi ho raha"
                ActionResult.ExecutedWithInfo("Phone ki battery $level% hai aur $status.")
            } else {
                ActionResult.Failed("Battery status access nahi ho saka.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("Battery level check karne mein dikkat aayi.")
        }
    }

    private fun getDateTimeInfo(): ActionResult {
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        return ActionResult.ExecutedWithInfo("Abhi samay $timeStr hai, aur aaj $dateStr hai.")
    }

    private fun getDeviceInfo(): ActionResult {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val androidVer = Build.VERSION.RELEASE
        val sdkVer = Build.VERSION.SDK_INT
        return ActionResult.ExecutedWithInfo("Yeh $manufacturer $model hai, jo Android $androidVer (API $sdkVer) par chal raha hai.")
    }

    // --- App Launching ---
    private fun openApplication(targetApp: String?): ActionResult {
        if (targetApp.isNullOrBlank()) {
            return ActionResult.Failed("Kaun si app kholni hai, kripya batayein.")
        }

        val name = targetApp.lowercase(Locale.ROOT).trim()

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "yt" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "wa" to "com.whatsapp",
            "camera" to "camera_intent",
            "photo" to "camera_intent",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "music" to "com.spotify.music",
            "gana" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "calculator" to "com.google.android.calculator",
            "calc" to "com.google.android.calculator",
            "hisab" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "ghadi" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "contacts" to "com.google.android.contacts",
            "phonebook" to "com.google.android.contacts",
            "files" to "com.google.android.documentsui",
            "settings" to "settings_intent",
            "setting" to "settings_intent"
        )

        try {
            val pm = context.packageManager

            if (name == "camera" || knownPackages[name] == "camera_intent") {
                val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(cameraIntent)
                return ActionResult.Handled("Camera open ho raha hai.")
            }

            if (name == "settings" || knownPackages[name] == "settings_intent") {
                return openSettings("main")
            }

            val pkg = knownPackages[name]
            if (pkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ActionResult.Handled("$targetApp open kar diya hai.")
                }
            }

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val appLabel = pm.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT)
                if (appLabel == name || appLabel.contains(name)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val displayName = pm.getApplicationLabel(appInfo).toString()
                        return ActionResult.Handled("$displayName open kar diya hai.")
                    }
                }
            }

            val marketUri = Uri.parse("market://search?q=${Uri.encode(targetApp)}")
            val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (marketIntent.resolveActivity(pm) != null) {
                context.startActivity(marketIntent)
                return ActionResult.Handled("'$targetApp' phone mein nahi mila, Play Store par search kiya ja raha hai.")
            }

            return ActionResult.Failed("Aapke phone mein '$targetApp' app nahi mili.")
        } catch (e: Exception) {
            return ActionResult.Failed("App open karne mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    // --- Android Settings ---
    private fun openSettings(target: String?): ActionResult {
        return try {
            val action = when (target?.lowercase(Locale.ROOT)?.trim()) {
                "wifi", "wi-fi", "internet" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display", "brightness", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS
                "battery", "power" -> Intent.ACTION_POWER_USAGE_SUMMARY
                "apps", "application", "applications" -> Settings.ACTION_APPLICATION_SETTINGS
                "datetime", "date", "time" -> Settings.ACTION_DATE_SETTINGS
                "airplane", "flight" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }

            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("Settings open kar di hain.")
        } catch (e: Exception) {
            ActionResult.Failed("Settings open nahi ho saki: ${e.localizedMessage}")
        }
    }

    // --- Web Search & URLs ---
    private fun launchUrl(rawUrl: String?): ActionResult {
        if (rawUrl.isNullOrBlank()) {
            return ActionResult.Failed("URL nahi mila.")
        }
        return try {
            val url = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                "https://$rawUrl"
            } else {
                rawUrl
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("$url browser mein open ho raha hai.")
        } catch (e: Exception) {
            ActionResult.Failed("URL open nahi ho saka.")
        }
    }

    private fun searchWeb(query: String?): ActionResult {
        return try {
            val q = query.orEmpty().trim()
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("Google par search kiya ja raha hai.")
        } catch (e: Exception) {
            ActionResult.Failed("Web search karne mein dikkat aayi.")
        }
    }

    // --- Alarm & Timer ---
    private fun setTimer(target: String?, params: Map<String, String>): ActionResult {
        return try {
            val secondsFromParam = params["seconds"]?.toIntOrNull()
            val secondsFromTarget = target?.filter { it.isDigit() }?.toIntOrNull()
            val totalSeconds = secondsFromParam ?: secondsFromTarget ?: 60

            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "Akriti Timer")
                putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("$totalSeconds seconds ka timer set kar diya hai.")
        } catch (e: Exception) {
            ActionResult.Failed("Timer set karne ke liye clock app nahi mili.")
        }
    }

    private fun setAlarm(target: String?, params: Map<String, String>, rawQuery: String?): ActionResult {
        return try {
            var hour = params["hour"]?.toIntOrNull()
            var minute = params["minute"]?.toIntOrNull() ?: 0

            if (hour == null && !target.isNullOrBlank()) {
                val timeParts = target.split(":")
                if (timeParts.size >= 2) {
                    hour = timeParts[0].filter { it.isDigit() }.toIntOrNull()
                    minute = timeParts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
                } else {
                    hour = target.filter { it.isDigit() }.toIntOrNull()
                }

                if (target.contains("pm", ignoreCase = true) && (hour ?: 0) < 12) {
                    hour = (hour ?: 0) + 12
                }
            }

            val finalHour = (hour ?: 7).coerceIn(0, 23)
            val finalMinute = minute.coerceIn(0, 59)

            // Schedule with AlarmManager & persistent store
            alarmScheduler.scheduleAlarm(finalHour, finalMinute, "Akriti Alarm")

            val timeDisplay = String.format(Locale.ROOT, "%02d:%02d", finalHour, finalMinute)
            ActionResult.Handled("$timeDisplay ka alarm set kar diya hai.")
        } catch (e: Exception) {
            ActionResult.Failed("Alarm set nahi ho saka: ${e.localizedMessage}")
        }
    }

    private fun cancelAlarm(target: String?, params: Map<String, String>, rawQuery: String?): ActionResult {
        return try {
            var hour = params["hour"]?.toIntOrNull()
            var minute = params["minute"]?.toIntOrNull()

            if (hour == null && !target.isNullOrBlank()) {
                val timeParts = target.split(":")
                if (timeParts.size >= 2) {
                    hour = timeParts[0].filter { it.isDigit() }.toIntOrNull()
                    minute = timeParts[1].filter { it.isDigit() }.toIntOrNull()
                } else {
                    hour = target.filter { it.isDigit() }.toIntOrNull()
                }

                if (target.contains("pm", ignoreCase = true) && (hour ?: 0) < 12) {
                    hour = (hour ?: 0) + 12
                }
            }

            if (hour != null) {
                val finalHour = hour.coerceIn(0, 23)
                val finalMinute = minute?.coerceIn(0, 59)
                val matchingAlarms = alarmScheduler.findMatchingAlarms(finalHour, finalMinute)

                if (matchingAlarms.isNotEmpty()) {
                    for (alarm in matchingAlarms) {
                        alarmScheduler.cancelAlarm(alarm)
                    }
                    val timeDisplay = if (finalMinute != null) {
                        String.format(Locale.ROOT, "%02d:%02d", finalHour, finalMinute)
                    } else {
                        String.format(Locale.ROOT, "%02d:00", finalHour)
                    }
                    ActionResult.Handled("$timeDisplay ka alarm cancel kar diya hai.")
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                                putExtra(AlarmClock.EXTRA_HOUR, finalHour)
                                if (finalMinute != null) {
                                    putExtra(AlarmClock.EXTRA_MINUTES, finalMinute)
                                }
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (dismissIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(dismissIntent)
                                val timeDisplay = String.format(Locale.ROOT, "%02d:%02d", finalHour, finalMinute ?: 0)
                                return ActionResult.Handled("$timeDisplay ka alarm cancel kar diya hai.")
                            }
                        } catch (_: Exception) {}
                    }
                    val timeDisplay = String.format(Locale.ROOT, "%02d:%02d", finalHour, finalMinute ?: 0)
                    ActionResult.Handled("Mujhe $timeDisplay ka koi active alarm nahi mila.")
                }
            } else {
                val activeAlarms = alarmScheduler.getActiveAlarms()
                if (activeAlarms.isNotEmpty()) {
                    for (alarm in activeAlarms) {
                        alarmScheduler.cancelAlarm(alarm)
                    }
                    ActionResult.Handled("Sabhi active alarms cancel kar diye gaye hain.")
                } else {
                    ActionResult.Handled("Koi active alarm nahi mila.")
                }
            }
        } catch (e: Exception) {
            ActionResult.Failed("Alarm cancel nahi ho saka: ${e.localizedMessage}")
        }
    }

    private fun setReminder(title: String?, rawQuery: String?): ActionResult {
        return try {
            val eventTitle = title?.ifBlank { "Akriti Reminder" } ?: "Akriti Reminder"
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, eventTitle)
                putExtra(CalendarContract.Events.DESCRIPTION, rawQuery ?: "Set via Akriti Voice Assistant")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("'$eventTitle' reminder calendar mein add karne ke liye open kar diya hai.")
        } catch (e: Exception) {
            ActionResult.Failed("Calendar reminder set karne mein dikkat aayi.")
        }
    }

    // --- Dialer & Contacts ---
    private fun dialPhone(phoneNumber: String?): ActionResult {
        return try {
            val cleaned = phoneNumber?.trim().orEmpty()
            if (cleaned.isBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ActionResult.Handled("Phone dialer open kar diya hai.")
            }

            val digitsCount = cleaned.count { it.isDigit() }
            val hasLetters = cleaned.any { it.isLetter() }

            if (!hasLetters && digitsCount > 0) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                return ActionResult.Handled("$cleaned dialer mein open kar diya hai.")
            }

            val matches = findContactsByName(cleaned)
            when {
                matches.isEmpty() -> ActionResult.Handled("Mujhe '$cleaned' naam ka koi contact nahi mila.")
                matches.size > 1 -> {
                    val list = matches.take(3).joinToString(", ") { "${it.name} (${it.number})" }
                    ActionResult.Handled("Mujhe '$cleaned' ke liye multiple contacts mile: $list. Kise call karoon?")
                }
                else -> {
                    val match = matches.first()
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.number}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    ActionResult.Handled("${match.name} ka number (${match.number}) dialer mein open kar diya hai.")
                }
            }
        } catch (e: Exception) {
            ActionResult.Failed("Dialer open karne mein dikkat aayi: ${e.message}")
        }
    }

    private fun findContactsByName(nameQuery: String): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val results = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$nameQuery%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val number = if (numIndex >= 0) cursor.getString(numIndex) else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        val cleanNum = number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                        if (results.none { it.number.replace(" ", "").replace("-", "") == cleanNum && it.name.equals(name, ignoreCase = true) }) {
                            results.add(ContactMatch(id, name, number))
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                val allContacts = getAllDeviceContacts()
                val fuzzyMatchedName = VoiceRecognitionCorrector.findFuzzyContactMatch(nameQuery, allContacts.map { it.name })
                if (fuzzyMatchedName != null) {
                    val matchedContacts = allContacts.filter { it.name.equals(fuzzyMatchedName, ignoreCase = true) }
                    results.addAll(matchedContacts)
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun getAllDeviceContacts(): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val list = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val number = if (numIndex >= 0) cursor.getString(numIndex) else ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        val cleanNum = number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                        if (list.none { it.number.replace(" ", "").replace("-", "") == cleanNum && it.name.equals(name, ignoreCase = true) }) {
                            list.add(ContactMatch(id, name, number))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun sendMessage(recipient: String?, messageBody: String?): ActionResult {
        return try {
            var targetNumber = recipient.orEmpty().trim()
            var displayName = recipient

            if (targetNumber.any { it.isLetter() }) {
                val matches = findContactsByName(targetNumber)
                if (matches.isNotEmpty()) {
                    targetNumber = matches.first().number
                    displayName = matches.first().name
                }
            }

            val uri = Uri.parse("smsto:$targetNumber")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                if (!messageBody.isNullOrBlank()) {
                    putExtra("sms_body", messageBody)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val msg = if (!displayName.isNullOrBlank()) "$displayName ko message compose open kiya hai." else "Messages app open kar di hai."
            ActionResult.Handled(msg)
        } catch (e: Exception) {
            ActionResult.Failed("Messaging app open nahi ho saki.")
        }
    }

    private fun toggleFlashlight(state: String?): ActionResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: return ActionResult.Failed("Camera service uplabdh nahi hai.")

                val cameraIdList = cameraManager.cameraIdList
                var rearCameraId: String? = null
                for (id in cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraId = id
                        break
                    }
                }

                val targetId = rearCameraId ?: cameraIdList.firstOrNull()
                    ?: return ActionResult.Failed("Phone mein flashlight nahi mili.")

                val turnOn = when (state?.lowercase(Locale.ROOT)) {
                    "on" -> true
                    "off" -> false
                    else -> !isTorchOn
                }

                cameraManager.setTorchMode(targetId, turnOn)
                isTorchOn = turnOn
                val statusText = if (turnOn) "Flashlight ON kar di gayi hai 🔦" else "Flashlight OFF kar di gayi hai"
                ActionResult.Handled(statusText)
            } else {
                ActionResult.Failed("Flashlight toggle is Android version par supported nahi hai.")
            }
        } catch (e: Exception) {
            ActionResult.Failed("Flashlight control karne mein dikkat aayi: ${e.localizedMessage}")
        }
    }

    private fun shareContent(text: String?): ActionResult {
        if (text.isNullOrBlank()) {
            return ActionResult.Failed("Share karne ke liye koi content nahi mila.")
        }
        return try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share via Akriti").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
            ActionResult.Handled("Share options open kar diye hain.")
        } catch (e: Exception) {
            ActionResult.Failed("Content share karne mein dikkat aayi.")
        }
    }
}
