package com.example.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import java.util.Locale

data class AppLaunchResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val packageName: String? = null
)

/**
 * Manager for launching installed Android applications using PackageManager and explicit Intents.
 * Supports natural language intent keyword parsing in English and Hindi (e.g. "Open YouTube", "YouTube kholo", "Launch WhatsApp").
 */
class AppLauncherManager(private val context: Context) {
    private val tag = "AppLauncherManager"

    // Well-known app package mappings for fast and accurate package resolution
    private val knownAppPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "gmail" to "com.google.android.gm",
        "mail" to "com.google.android.gm",
        "email" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "photos" to "com.google.android.apps.photos",
        "google photos" to "com.google.android.apps.photos",
        "gallery" to "com.google.android.apps.photos",
        "drive" to "com.google.android.apps.docs",
        "google drive" to "com.google.android.apps.docs",
        "play store" to "com.android.vending",
        "playstore" to "com.android.vending",
        "store" to "com.android.vending",
        "camera" to "com.android.camera",
        "calculator" to "com.google.android.calculator",
        "calc" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "alarm" to "com.google.android.deskclock",
        "settings" to "com.android.settings",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "contacts" to "com.google.android.contacts",
        "snapchat" to "com.snapchat.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "netflix" to "com.netflix.mediaclient",
        "prime video" to "com.amazon.avod.thirdpartyclient",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nfc.payment",
        "google pay" to "com.google.android.apps.nfc.payment",
        "uber" to "com.ubercab",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android"
    )

    /**
     * Resolves the package name for a target app name query and opens it using an explicit Intent.
     * @param appNameQuery Target app name (e.g. "YouTube", "WhatsApp", "Calculator")
     */
    fun openAppByName(appNameQuery: String): AppLaunchResult {
        val cleanQuery = appNameQuery.lowercase(Locale.ROOT).trim()
        if (cleanQuery.isBlank()) {
            return AppLaunchResult(false, "App name is empty.")
        }

        val packageManager = context.packageManager

        // 1. Check known package dictionary for instant explicit intent lookup
        val knownPackage = knownAppPackages[cleanQuery]
        if (knownPackage != null) {
            val result = launchAppByPackageName(knownPackage, appNameQuery)
            if (result.isHandled) return result
        }

        // 2. Query PackageManager dynamically for launcher activities matching app title or package name
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)

            for (resolveInfo in resolveInfos) {
                val appLabel = resolveInfo.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
                val packageName = resolveInfo.activityInfo.packageName.lowercase(Locale.ROOT)

                if (appLabel == cleanQuery || appLabel.contains(cleanQuery) || cleanQuery.contains(appLabel)) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val displayName = resolveInfo.loadLabel(packageManager).toString()
                        Log.i(tag, "Successfully launched app: $displayName ($packageName)")
                        return AppLaunchResult(
                            isHandled = true,
                            feedbackMessage = "Opening $displayName...",
                            packageName = resolveInfo.activityInfo.packageName
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying installed applications: ${e.message}", e)
        }

        // 3. Fallback to system utility intent actions if explicit package wasn't directly found
        val fallbackResult = launchSystemUtilityFallback(cleanQuery)
        if (fallbackResult.isHandled) {
            return fallbackResult
        }

        return AppLaunchResult(
            isHandled = false,
            feedbackMessage = "Could not find application \"$appNameQuery\" installed on this device.",
            packageName = null
        )
    }

    /**
     * Finds explicit launch Intent for given package name and triggers context.startActivity(launchIntent).
     */
    fun launchAppByPackageName(packageName: String, displayName: String = packageName): AppLaunchResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.i(tag, "Successfully launched package: $packageName")
                val formattedName = displayName.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
                AppLaunchResult(
                    isHandled = true,
                    feedbackMessage = "Opening $formattedName...",
                    packageName = packageName
                )
            } else {
                AppLaunchResult(false, "App package $packageName is not installed or cannot be launched.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error launching package $packageName: ${e.message}", e)
            AppLaunchResult(false, "Failed to launch app: ${e.localizedMessage}")
        }
    }

    /**
     * Fallback launcher for standard system utilities using implicit system intent actions.
     */
    private fun launchSystemUtilityFallback(query: String): AppLaunchResult {
        return try {
            when {
                query.contains("camera") || query.contains("कैमरा") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening Camera...", "com.android.camera")
                }
                query.contains("calculator") || query.contains("कैल्कुलेटर") || query.contains("calc") -> {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_CALCULATOR)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening Calculator...", "com.google.android.calculator")
                }
                query.contains("settings") || query.contains("सेटिंग्स") -> {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening Settings...", "com.android.settings")
                }
                query.contains("dialer") || query.contains("phone") || query.contains("फोन") -> {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Opening Phone Dialer...", "com.google.android.dialer")
                }
                else -> AppLaunchResult(false, "No fallback available for $query")
            }
        } catch (e: Exception) {
            Log.e(tag, "Fallback launch failed for $query: ${e.message}")
            AppLaunchResult(false, "Could not open requested system utility.")
        }
    }

    /**
     * Parses spoken commands containing intent keywords (e.g. "Open YouTube", "YouTube kholo", "Launch WhatsApp"),
     * extracts the app name, finds its package name, and opens the app immediately via Intent.
     */
    fun processVoiceAppLaunchCommand(spokenCommand: String): AppLaunchResult {
        val q = spokenCommand.lowercase(Locale.ROOT).trim()
        if (q.isBlank()) return AppLaunchResult(false, "Empty command.")

        // List of launch intent keywords in English and Hindi
        val launchKeywords = listOf(
            "open", "launch", "start", "run", "kholo", "chalao", "on karo",
            "khol do", "open karo", "chalu karo", "kholiye", "kholna", "chala do"
        )

        val hasKeyword = launchKeywords.any { q.contains(it) }
        if (!hasKeyword) {
            return AppLaunchResult(false, "No app launch intent keyword detected.")
        }

        // Extract app target name by stripping launch keywords
        var targetAppName = q
        for (kw in launchKeywords) {
            targetAppName = targetAppName.replace(kw, "")
        }
        targetAppName = targetAppName
            .replace("app", "")
            .replace("application", "")
            .replace("ko", "")
            .trim()

        if (targetAppName.isBlank()) {
            return AppLaunchResult(false, "No app name specified in command.")
        }

        Log.i(tag, "Extracted app launch query: \"$targetAppName\" from command: \"$spokenCommand\"")
        return openAppByName(targetAppName)
    }
}
