package com.example.update

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Single, crash-proof entry point for making sure the default [FirebaseApp] exists.
 *
 * Normally Firebase is initialised automatically by `FirebaseInitProvider` (a ContentProvider that
 * the Firebase SDK merges into the manifest) using the string resources that the Google Services
 * Gradle plugin generates from `google-services.json`. If those resources are missing, nothing
 * initialises Firebase and every `getInstance()` call throws. This helper:
 *  - returns immediately when Firebase is already initialised,
 *  - otherwise tries `FirebaseApp.initializeApp(context)` (reads the same generated resources;
 *    no keys or options are hardcoded anywhere),
 *  - never throws; it returns false and logs the likely reason instead.
 */
object FirebaseBootstrap {
    private const val TAG = "FirebaseBootstrap"

    const val NOT_CONFIGURED_MESSAGE =
        "Update service is not configured in this build (Firebase config missing)."

    /** @return true when the default FirebaseApp is ready to use. Safe to call repeatedly. */
    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        return try {
            isInitialized(appContext) || initialize(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
            false
        }
    }

    private fun isInitialized(context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    @Synchronized
    private fun initialize(context: Context): Boolean {
        if (isInitialized(context)) return true
        // Returns null (instead of throwing) when the google-services resources are missing.
        val app = FirebaseApp.initializeApp(context)
        if (app == null) {
            logMissingConfig(context)
            return false
        }
        Log.i(TAG, "Default FirebaseApp initialized manually")
        return true
    }

    private fun logMissingConfig(context: Context) {
        val hasAppId = context.resources
            .getIdentifier("google_app_id", "string", context.packageName) != 0
        if (!hasAppId) {
            Log.e(
                TAG,
                "Firebase is NOT configured: string resource 'google_app_id' does not exist. " +
                    "The Google Services Gradle plugin (com.google.gms.google-services) is not applied " +
                    "to the app module, or google-services.json is missing / has no client for " +
                    "package '${context.packageName}'. The in-app updater is disabled."
            )
        } else {
            Log.e(
                TAG,
                "Firebase options could not be built from the generated resources even though " +
                    "'google_app_id' exists. Re-download google-services.json for package " +
                    "'${context.packageName}' and rebuild. The in-app updater is disabled."
            )
        }
    }
}
