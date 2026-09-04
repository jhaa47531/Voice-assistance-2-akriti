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
import com.example.data.model.ActionType
import com.example.data.model.IntentCommand
import com.example.data.repository.VoiceNotesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactMatch(val id: String, val name: String, val number: String)

sealed class ActionResult {
    data class Handled(val message: String) : ActionResult()
    data class ExecutedWithInfo(val info: String) : ActionResult()
    data class Failed(val error: String) : ActionResult()
    object Ignored : ActionResult()
}

class AndroidActionHandler(
    private val context: Context,
    private val notesRepository: VoiceNotesRepository? = null,
    private val onShowNotes: (() -> Unit)? = null
) {

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
            ActionType.BATTERY_INFO -> getBatteryInfo()
            ActionType.DATE_TIME -> getDateTimeInfo()
            ActionType.DEVICE_INFO -> getDeviceInfo()
            ActionType.OPEN_APP -> openApplication(intent.target)
            ActionType.OPEN_SETTINGS -> openSettings(intent.target)
            ActionType.SEARCH_WEB -> searchWeb(intent.target ?: intent.rawQuery)
            ActionType.LAUNCH_URL -> launchUrl(intent.target)
            ActionType.SET_TIMER -> setTimer(intent.target, intent.parameters)
            ActionType.SET_ALARM -> setAlarm(intent.target, intent.parameters, intent.rawQuery)
            ActionType.SET_REMINDER -> setReminder(intent.target, intent.rawQuery)
            ActionType.TAKE_NOTE -> saveVoiceNote(intent.target ?: intent.rawQuery)
            ActionType.SHOW_NOTES -> showNotesList()
            ActionType.DIAL_PHONE -> dialPhone(intent.target)
            ActionType.SEND_MESSAGE -> sendMessage(intent.target, intent.rawQuery)
            ActionType.TOGGLE_FLASHLIGHT -> toggleFlashlight(intent.target)
            ActionType.SHARE_CONTENT -> shareContent(intent.target ?: intent.rawQuery)
            ActionType.NONE -> ActionResult.Ignored
        }
    }

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

    private fun openApplication(targetApp: String?): ActionResult {
        if (targetApp.isNullOrBlank()) {
            return ActionResult.Failed("Kaun si app kholni hai, kripya batayein.")
        }

        val name = targetApp.lowercase(Locale.ROOT).trim()

        // Known common Android app package mappings
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "camera" to "camera_intent",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "mail" to "com.google.android.gm",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "contacts" to "com.google.android.contacts",
            "files" to "com.google.android.documentsui",
            "settings" to "settings_intent"
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

            // Check known package
            val pkg = knownPackages[name]
            if (pkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ActionResult.Handled("$targetApp open kar diya hai.")
                }
            }

            // Search installed apps matching target label or package
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

            // If not found installed, provide Play Store link
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
            ActionResult.Failed("Timer set karne ke liye alarm/clock app nahi mili.")
        }
    }

    private fun setAlarm(target: String?, params: Map<String, String>, rawQuery: String?): ActionResult {
        return try {
            var hour = params["hour"]?.toIntOrNull()
            var minute = params["minute"]?.toIntOrNull() ?: 0

            // If not in params, parse from target (e.g. "07:30", "7", "19:45")
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

            val finalHour = hour ?: 7
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "Akriti Alarm")
                putExtra(AlarmClock.EXTRA_HOUR, finalHour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Handled("$finalHour:${String.format("%02d", minute)} ka alarm set kar diya hai.")
        } catch (e: Exception) {
            ActionResult.Failed("Alarm set nahi ho saka.")
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

            // Check if it's purely digits or phone format (+, -, digits)
            val digitsCount = cleaned.count { it.isDigit() }
            val hasLetters = cleaned.any { it.isLetter() }

            if (!hasLetters && digitsCount > 0) {
                // Pure phone number
                return initiateCallOrDial(cleaned, cleaned)
            }

            // Otherwise, target is a contact name
            val hasReadContacts = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasReadContacts) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ActionResult.Handled("Contacts access karne ke liye permission enable kijiye. Dialer open kar diya hai.")
            }

            val matchingContacts = findContactsByName(cleaned)

            when {
                matchingContacts.isEmpty() -> {
                    ActionResult.Handled("Mujhe '$cleaned' naam ka koi contact nahi mila.")
                }
                matchingContacts.size > 1 -> {
                    val namesList = matchingContacts.take(3).joinToString(", ") { "${it.name} (${it.number})" }
                    ActionResult.Handled("Mujhe '$cleaned' ke liye multiple contacts mile: $namesList. Kise call karoon?")
                }
                else -> {
                    val match = matchingContacts.first()
                    initiateCallOrDial(match.number, match.name)
                }
            }
        } catch (e: Exception) {
            ActionResult.Failed("Call lagane mein dikkat aayi: ${e.message}")
        }
    }

    private fun initiateCallOrDial(phoneNumber: String, contactDisplayName: String): ActionResult {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasCallPermission) {
            // Direct call without stopping at dialer
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            ActionResult.Handled("$contactDisplayName ko direct call lagayi ja rahi hai.")
        } else {
            // Graceful fallback to dialer with prefilled number
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ActionResult.Handled("$contactDisplayName ka number ($phoneNumber) dialer mein open kar diya hai.")
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
        } catch (e: Exception) {
            // Return whatever matches were found
        }
        return results
    }

    private fun sendMessage(recipient: String?, messageBody: String?): ActionResult {
        return try {
            var targetNumber = recipient.orEmpty().trim()
            var displayName = recipient

            // If recipient has letters, attempt to look up their contact phone number
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
                if (cameraManager == null) {
                    return ActionResult.Failed("Camera service uplabdh nahi hai.")
                }

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
                if (targetId == null) {
                    return ActionResult.Failed("Phone mein flashlight nahi mili.")
                }

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
