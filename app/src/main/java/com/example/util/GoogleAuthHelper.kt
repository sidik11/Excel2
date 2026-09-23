package com.example.util

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Patterns

object GoogleAuthHelper {

    /**
     * Builds an intent to launch the system Google account chooser.
     * Works on all Android versions and device types without requiring Play Services backend registration.
     */
    fun createGoogleAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    /**
     * Extracts selected Google Account email from AccountManager result.
     */
    fun parseAccountPickerResult(resultCode: Int, data: Intent?): String? {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank() && isValidEmail(accountName)) {
                return accountName
            }
        }
        return null
    }

    /**
     * Validates an email address.
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    /**
     * Derives a clean readable display name from an email address (e.g. john.doe@gmail.com -> John Doe).
     */
    fun deriveDisplayNameFromEmail(email: String): String {
        val username = email.substringBefore("@")
        return username
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    /**
     * Connects Google account to the user's profile and synchronizes with fingerprint.dat.
     */
    fun connectGoogleAccount(
        context: Context,
        email: String,
        displayName: String = ""
    ): Pair<Boolean, String> {
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail)) {
            return Pair(false, "Please enter a valid Google Account email.")
        }

        val name = if (displayName.isNotBlank()) displayName.trim() else deriveDisplayNameFromEmail(cleanEmail)
        return ProfileManager.connectGoogleAccount(
            context = context,
            email = cleanEmail,
            displayName = name
        )
    }

    /**
     * Disconnects Google account.
     */
    fun disconnectGoogleAccount(context: Context): Pair<Boolean, String> {
        return ProfileManager.disconnectGoogleAccount(context)
    }
}
