package com.example.arushi.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class ActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val error: String? = null
)

sealed class ContactSearchResult {
    data class Success(val name: String, val phoneNumber: String) : ContactSearchResult()
    data class Multiple(val matches: List<ContactInfo>) : ContactSearchResult()
    data class NotFound(val query: String) : ContactSearchResult()
    data class PermissionRequired(val message: String) : ContactSearchResult()
    data class Error(val error: String) : ContactSearchResult()
}

data class ContactInfo(val name: String, val phoneNumber: String)

class AndroidActionManager(private val context: Context) {

    companion object {
        private const val TAG = "AndroidActionManager"

        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "email" to "com.google.android.gm"
        )
    }

    /**
     * Requirement 20: Open WhatsApp via intent/deep link
     */
    fun openWhatsApp(): ActionResult {
        Log.d(TAG, "Executing openWhatsApp action")
        val pm = context.packageManager

        // 1. Try launching the official WhatsApp app directly
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
            ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(launchIntent)
                ActionResult(
                    success = true,
                    action = "openWhatsApp",
                    message = "Opened WhatsApp successfully."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error launching WhatsApp intent", e)
                ActionResult(
                    success = false,
                    action = "openWhatsApp",
                    message = "Failed to launch WhatsApp.",
                    error = e.message
                )
            }
        }

        // 2. Try URI intent
        return try {
            val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
            uriIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(uriIntent)
            ActionResult(
                success = true,
                action = "openWhatsApp",
                message = "Opened WhatsApp via deep link."
            )
        } catch (e: Exception) {
            Log.w(TAG, "WhatsApp is not installed on this device", e)
            ActionResult(
                success = false,
                action = "openWhatsApp",
                message = "WhatsApp is not installed on this device.",
                error = "WhatsApp not installed"
            )
        }
    }

    /**
     * Requirement 21: Generic App Opening with safe allowlist
     */
    fun openApp(appName: String): ActionResult {
        val cleanName = appName.trim().lowercase()
        Log.d(TAG, "Executing openApp for: $cleanName")

        // Check special intents first
        when {
            cleanName == "whatsapp" -> return openWhatsApp()
            cleanName.contains("camera") -> {
                return try {
                    val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(cameraIntent)
                    ActionResult(true, "openApp", "Opened Camera.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Failed to open Camera.", e.message)
                }
            }
            cleanName.contains("setting") -> {
                return try {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                    ActionResult(true, "openApp", "Opened Settings.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Failed to open Settings.", e.message)
                }
            }
            cleanName.contains("dialer") || cleanName.contains("phone") -> {
                return try {
                    val dialerIntent = Intent(Intent.ACTION_DIAL)
                    dialerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(dialerIntent)
                    ActionResult(true, "openApp", "Opened Phone dialer.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Failed to open dialer.", e.message)
                }
            }
        }

        val targetPackage = APP_PACKAGES[cleanName]
            ?: APP_PACKAGES.entries.firstOrNull { cleanName.contains(it.key) }?.value

        if (targetPackage != null) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(intent)
                    ActionResult(true, "openApp", "Opened $appName successfully.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Could not launch $appName.", e.message)
                }
            } else {
                return ActionResult(
                    success = false,
                    action = "openApp",
                    message = "$appName is not installed on this device.",
                    error = "App not installed"
                )
            }
        }

        // Try searching installed applications by label
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(cleanName) || cleanName.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return ActionResult(true, "openApp", "Opened $label successfully.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps", e)
        }

        return ActionResult(
            success = false,
            action = "openApp",
            message = "Could not find an app named '$appName' on your device.",
            error = "App not found"
        )
    }

    /**
     * Requirement 22: Open Website / URL
     */
    fun openWebsite(url: String): ActionResult {
        Log.d(TAG, "Executing openWebsite: $url")
        var validUrl = url.trim()
        if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
            validUrl = "https://$validUrl"
        }

        return try {
            val uri = Uri.parse(validUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult(true, "openWebsite", "Opened $validUrl in browser.")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening website $validUrl", e)
            ActionResult(false, "openWebsite", "Failed to open $validUrl.", e.message)
        }
    }

    /**
     * Requirement 23: Phone Calling
     */
    fun makeCall(phoneNumber: String): ActionResult {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            return ActionResult(false, "makeCall", "Invalid phone number provided.", "Empty number")
        }

        Log.d(TAG, "Executing makeCall: $cleanNumber")
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dialIntent)
            ActionResult(true, "makeCall", "Opened phone dialer for $cleanNumber.")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching dialer for $cleanNumber", e)
            ActionResult(false, "makeCall", "Could not start call to $cleanNumber.", e.message)
        }
    }

    /**
     * Requirement 24: Call Contact by Name
     */
    fun searchContact(contactName: String): ContactSearchResult {
        val cleanName = contactName.trim().lowercase()
        Log.d(TAG, "Searching contacts for: $cleanName")

        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactSearchResult.PermissionRequired("Contacts permission is required to search contacts.")
        }

        val matches = mutableListOf<ContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue

                    if (displayName.lowercase().contains(cleanName) || cleanName.contains(displayName.lowercase())) {
                        if (matches.none { it.name == displayName && it.phoneNumber == number }) {
                            matches.add(ContactInfo(displayName, number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            return ContactSearchResult.Error("Failed to access contacts: ${e.message}")
        }

        return when {
            matches.isEmpty() -> ContactSearchResult.NotFound(contactName)
            matches.size == 1 -> {
                val contact = matches.first()
                makeCall(contact.phoneNumber)
                ContactSearchResult.Success(contact.name, contact.phoneNumber)
            }
            else -> ContactSearchResult.Multiple(matches)
        }
    }
}
