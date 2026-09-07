package com.example.voice

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.min

object VoiceRecognitionCorrector {

    /**
     * Known acoustic/homophone mishearings where Indian names or Hinglish words
     * are frequently transcribed into common English dictionary words by speech recognizers.
     * Only used in safe contexts (e.g. contact search, command verification).
     */
    private val KNOWN_NAME_HOMOPHONES = mapOf(
        "aunt" to listOf("ansh", "anshu", "ant", "amit"),
        "ant" to listOf("ansh", "anant"),
        "unsh" to listOf("ansh"),
        "current" to listOf("karan"),
        "rowan" to listOf("rohan"),
        "puja" to listOf("pooja"),
        "pooja" to listOf("puja"),
        "dee" to listOf("di", "didi"),
        "de" to listOf("di", "didi"),
        "the" to listOf("di")
    )

    /**
     * Given the candidate hypotheses from SpeechRecognizer, selects the most accurate
     * candidate based on device contacts, assistant command keywords, and safe context.
     */
    fun selectBestCandidate(context: Context, candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        val trimmed = candidates.map { it.trim() }.filter { it.isNotBlank() }
        if (trimmed.isEmpty()) return ""
        if (trimmed.size == 1) {
            return fixAssistantKeywords(trimmed.first())
        }

        // 1. Check if any candidate has a direct match in device contacts
        val contactMatch = findBestContactCandidate(context, trimmed)
        if (contactMatch != null) {
            return contactMatch
        }

        // 2. Check if any candidate matches assistant command keywords
        val commandMatch = findBestCommandCandidate(trimmed)
        if (commandMatch != null) {
            return commandMatch
        }

        // Default to the first candidate with assistant keyword cleanup
        return fixAssistantKeywords(trimmed.first())
    }

    /**
     * Checks all candidate strings against device contacts.
     * If candidate 0 does not match any contact, but an alternate candidate does,
     * the matching candidate is selected.
     */
    private fun findBestContactCandidate(context: Context, candidates: List<String>): String? {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val allContactNames = getAllContactNames(context)
        if (allContactNames.isEmpty()) return null

        // Pass A: Check for candidates where extracted name directly equals a contact
        for (cand in candidates) {
            val extractedName = extractNameFromCandidate(cand)
            if (extractedName.isNotBlank()) {
                val matchesContact = allContactNames.any { it.equals(extractedName, ignoreCase = true) }
                if (matchesContact) {
                    return cand
                }
            }
        }

        // Pass B: Check if candidate itself is a contact name
        for (cand in candidates) {
            val isExactContact = allContactNames.any { it.equals(cand, ignoreCase = true) }
            if (isExactContact) {
                return cand
            }
        }

        // Pass C: Check if candidate contains a contact name as a distinct word
        for (cand in candidates) {
            val words = cand.split("\\s+".toRegex())
            for (word in words) {
                if (word.length >= 3 && allContactNames.any { it.equals(word, ignoreCase = true) }) {
                    return cand
                }
            }
        }

        return null
    }

    /**
     * Retrieves distinct contact display names from the device.
     */
    fun getAllContactNames(context: Context): Set<String> {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptySet()
        }

        val names = mutableSetOf<String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)?.trim().orEmpty()
                        if (name.isNotBlank()) {
                            names.add(name)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return names
    }

    /**
     * Extracts target contact name from calling/messaging sentences.
     */
    private fun extractNameFromCandidate(candidate: String): String {
        val q = candidate.lowercase(Locale.ROOT).trim()

        // "make a call to ansh", "call to ansh", "phone to ansh"
        val callToMatch = Regex("(?i)^(?:make\\s+a\\s+(?:phone\\s+)?call\\s+to|call\\s+to|phone\\s+to)\\s+(.+)").find(candidate)
        if (callToMatch != null) {
            return callToMatch.groupValues[1].trim()
        }

        // "call ansh", "dial ansh", "phone ansh"
        if (q.startsWith("call ") || q.startsWith("dial ") || q.startsWith("phone ")) {
            return candidate.substringAfter(" ").trim()
        }

        // "ansh ko call karo", "ansh ko phone lagao"
        val koCallRegex = Regex("(?i)^(.+?)\\s+ko\\s+(?:call|phone)\\s*(?:karo|lagao)?$")
        koCallRegex.find(candidate)?.let {
            return it.groupValues[1].trim()
        }

        // "message ansh"
        if (q.startsWith("message ") || q.startsWith("sms ")) {
            return candidate.substring(8).trim()
        }

        return ""
    }

    /**
     * Identifies if an alternate candidate is a well-formed assistant action.
     */
    private fun findBestCommandCandidate(candidates: List<String>): String? {
        val patterns = listOf(
            Regex("(?i)^(torch|flashlight)\\s+(on|off|toggle|jalao|bujhao|chalao|band)"),
            Regex("(?i)^(open|kholo|khol do)\\s+[a-z0-9]+"),
            Regex("(?i)^(call|phone|dial)\\s+[a-z0-9]+"),
            Regex("(?i)^(.+?)\\s+ko\\s+(call|phone)\\s*(karo|lagao)?"),
            Regex("(?i)^(sun akriti|hey akriti|hello akriti)"),
            Regex("(?i)^(take a note|note down|note karo)\\s*")
        )

        for (candidate in candidates) {
            for (pattern in patterns) {
                if (pattern.containsMatchIn(candidate.trim())) {
                    return fixAssistantKeywords(candidate)
                }
            }
        }
        return null
    }

    /**
     * Performs safe, conservative keyword adjustments for known assistant phrases.
     */
    fun fixAssistantKeywords(input: String): String {
        var text = input.trim()

        // Safe wake word / assistant name adjustments
        if (text.startsWith("sun a kriti", ignoreCase = true)) {
            text = text.replaceFirst(Regex("(?i)^sun a kriti"), "sun akriti")
        }
        if (text.startsWith("son akriti", ignoreCase = true)) {
            text = text.replaceFirst(Regex("(?i)^son akriti"), "sun akriti")
        }
        if (text.startsWith("a kriti", ignoreCase = true)) {
            text = text.replaceFirst(Regex("(?i)^a kriti"), "akriti")
        }

        // Common app launch phrase: "cold do" -> "khol do"
        if (text.contains("cold do", ignoreCase = true)) {
            text = text.replace(Regex("(?i)\\bcold do\\b"), "khol do")
        }

        return text
    }

    /**
     * Checks if a contact name query safely resolves to a contact via homophones or high similarity.
     * E.g. "aunt" query when user has a contact named "Ansh" and NO contact named "Aunt".
     */
    fun findFuzzyContactMatch(
        query: String,
        availableContacts: List<String>
    ): String? {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isBlank() || availableContacts.isEmpty()) return null

        // 1. Check known homophone mappings (e.g. "aunt" -> "ansh")
        KNOWN_NAME_HOMOPHONES[cleanQuery]?.let { possibleNames ->
            for (candidateName in possibleNames) {
                val match = availableContacts.firstOrNull { it.equals(candidateName, ignoreCase = true) }
                if (match != null) {
                    return match
                }
            }
        }

        // 1b. Check prefix or word token match (e.g. "ansh" matches "ansh sharma", "di" matches "di airtel")
        val prefixOrWordMatch = availableContacts.firstOrNull { contact ->
            val contactLower = contact.trim().lowercase(Locale.ROOT)
            contactLower == cleanQuery ||
            contactLower.startsWith("$cleanQuery ") ||
            contactLower.split("\\s+".toRegex()).any { it == cleanQuery }
        }
        if (prefixOrWordMatch != null) {
            return prefixOrWordMatch
        }

        // 2. Levenshtein edit distance for short/medium names
        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        for (contact in availableContacts) {
            val contactLower = contact.trim().lowercase(Locale.ROOT)
            val dist = levenshteinDistance(cleanQuery, contactLower)

            // Safe threshold: max 2 edits for length >= 4
            val maxAllowed = if (cleanQuery.length <= 4) 2 else 3
            if (dist <= maxAllowed && dist < minDistance) {
                minDistance = dist
                bestMatch = contact
            }
        }

        return bestMatch
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
