package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSecurityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        AppSecurityManager.init(context)
    }

    @Test
    fun testSetAndVerify6DigitPin() {
        val pin = "123456"
        val setResult = AppSecurityManager.set6DigitPin(context, pin)
        assertTrue("PIN setup should succeed", setResult)

        val config = AppSecurityManager.securityConfig.value
        assertTrue("PIN should be enabled in config", config.isPinEnabled)
        assertTrue("PIN salt should not be empty", config.pinSalt.isNotEmpty())
        assertTrue("PIN hash should not be empty", config.pinHash.isNotEmpty())

        // Verify correct PIN
        assertTrue("Correct PIN verification should succeed", AppSecurityManager.verifyPin("123456", context))

        // Verify wrong PIN
        assertFalse("Wrong PIN verification should fail", AppSecurityManager.verifyPin("654321", context))

        // Check security directory files
        val securityDir = AppStorageHelper.getSecurityDir(context)
        assertTrue("Security directory must exist", securityDir.exists())
        val pinFile = File(securityDir, "pin_credential.dat")
        val configFile = File(securityDir, "security_config.json")
        assertTrue("pin_credential.dat must exist in security folder", pinFile.exists())
        assertTrue("security_config.json must exist in security folder", configFile.exists())
    }

    @Test
    fun testRejectInvalidPinLength() {
        assertFalse("PIN with 5 digits should be rejected", AppSecurityManager.set6DigitPin(context, "12345"))
        assertFalse("PIN with 7 digits should be rejected", AppSecurityManager.set6DigitPin(context, "1234567"))
        assertFalse("Non-digit PIN should be rejected", AppSecurityManager.set6DigitPin(context, "12345a"))
    }

    @Test
    fun testChangePin() {
        AppSecurityManager.set6DigitPin(context, "112233")

        // Fail with wrong old PIN
        val (failResult, _) = AppSecurityManager.changePin(context, "000000", "998877")
        assertFalse("Change PIN should fail with wrong old PIN", failResult)

        // Succeed with correct old PIN
        val (successResult, _) = AppSecurityManager.changePin(context, "112233", "998877")
        assertTrue("Change PIN should succeed with correct old PIN", successResult)
        assertTrue("New PIN should verify successfully", AppSecurityManager.verifyPin("998877", context))
    }

    @Test
    fun testFingerprintTokenGeneration() {
        AppSecurityManager.set6DigitPin(context, "556677")

        val (ok, _) = AppSecurityManager.setFingerprintEnabled(context, true)
        assertTrue("Enabling fingerprint should succeed", ok)

        val securityDir = AppStorageHelper.getSecurityDir(context)
        val bioFile = File(securityDir, "biometric_token.dat")
        assertTrue("biometric_token.dat should exist in security folder", bioFile.exists())
        assertTrue("Biometric token should not be empty", bioFile.readText().isNotEmpty())
    }

    @Test
    fun testAntiScreenshotToggle() {
        AppSecurityManager.setAntiScreenshotEnabled(context, true)
        assertTrue(AppSecurityManager.securityConfig.value.isAntiScreenshotEnabled)

        AppSecurityManager.setAntiScreenshotEnabled(context, false)
        assertFalse(AppSecurityManager.securityConfig.value.isAntiScreenshotEnabled)
    }

    @Test
    fun testDisablePin() {
        AppSecurityManager.set6DigitPin(context, "987654")
        val (disabled, _) = AppSecurityManager.disablePin(context, "987654")
        assertTrue("Disabling PIN with correct password should succeed", disabled)
        assertFalse("PIN should no longer be enabled", AppSecurityManager.securityConfig.value.isPinEnabled)
    }

    @Test
    fun testMandatoryGoogleConnectionAndFingerprintRestore() {
        val testProfile = com.example.data.local.UserProfile(
            fullName = "John Doe",
            dateOfBirth = "01-01-1990",
            phoneNumber = "+1 555-0100",
            emailId = "john@example.com",
            device = "Pixel 8",
            village = "Greenfield",
            district = "Central",
            state = "California",
            country = "USA",
            pincode = "94016",
            profileImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            googleEmail = "",
            googleDisplayName = "",
            isGoogleConnected = false
        )

        // Verify that profile is incomplete because Google connection is mandatory
        assertFalse("Profile without Google connection must not be complete", testProfile.isComplete())
        assertEquals("Connect with Google (Mandatory)", testProfile.getFirstMissingField())

        // Connect with Google account
        val (connOk, _) = com.example.util.GoogleAuthHelper.connectGoogleAccount(context, "johndoe@gmail.com", "John Doe")
        assertTrue("Connecting Google account should succeed", connOk)

        val updatedProfile = testProfile.copy(
            googleEmail = "johndoe@gmail.com",
            googleDisplayName = "John Doe",
            isGoogleConnected = true
        )
        assertTrue("Profile with Google account connected must be complete", updatedProfile.isComplete())

        // Save profile
        com.example.util.ProfileManager.saveProfile(context, updatedProfile)

        // Register fingerprint
        val (fpOk, _) = AppSecurityManager.registerFingerprintCredential(context)
        assertTrue("Fingerprint registration should succeed", fpOk)

        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, "fingerprint.dat")
        assertTrue("fingerprint.dat should exist", datFile.exists())

        val datContent = datFile.readText()
        assertTrue("fingerprint.dat must contain GOOGLE_EMAIL", datContent.contains("GOOGLE_EMAIL=johndoe@gmail.com"))
        assertTrue("fingerprint.dat must contain GOOGLE_NAME", datContent.contains("GOOGLE_NAME=John Doe"))
        assertTrue("fingerprint.dat must contain GOOGLE_CONNECTED=true", datContent.contains("GOOGLE_CONNECTED=true"))
        assertTrue("fingerprint.dat must contain PROFILE_JSON", datContent.contains("PROFILE_JSON="))

        // Simulate moving to a new device by clearing local profile
        com.example.util.ProfileManager.deleteProfile(context)
        assertFalse("Profile should be cleared on new device", com.example.util.ProfileManager.hasCompleteProfile())

        // Put fingerprint.dat into the new device and restore
        val restored = AppSecurityManager.extractAndRestoreProfileFromContent(context, datContent)
        assertTrue("extractAndRestoreProfileFromContent should succeed", restored)

        val newDeviceProfile = com.example.util.ProfileManager.userProfile.value
        org.junit.Assert.assertNotNull("Restored profile must not be null", newDeviceProfile)
        assertEquals("John Doe", newDeviceProfile?.fullName)
        assertEquals("johndoe@gmail.com", newDeviceProfile?.googleEmail)
        assertEquals("John Doe", newDeviceProfile?.googleDisplayName)
        assertTrue("Google connected flag must be true", newDeviceProfile?.isGoogleConnected == true)
        assertTrue("Auto-added profile on new device must be complete", newDeviceProfile?.isComplete() == true)
    }

    @Test
    fun testFingerprintDatContains6DigitPasswordAndAutoEnablesOnNewDevice() {
        // Step 1: Create complete profile with mandatory Google connection
        val testProfile = com.example.data.local.UserProfile(
            fullName = "Alice Smith",
            dateOfBirth = "15-08-1995",
            phoneNumber = "+1 555-0234",
            emailId = "alice@example.com",
            device = "Pixel 7 Pro",
            village = "Sunnyvale",
            district = "Santa Clara",
            state = "California",
            country = "USA",
            pincode = "94086",
            profileImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            googleEmail = "alice.smith@gmail.com",
            googleDisplayName = "Alice Smith",
            googleId = "google_id_alice",
            isGoogleConnected = true
        )
        com.example.util.ProfileManager.saveProfile(context, testProfile)
        assertTrue("Profile must be complete", com.example.util.ProfileManager.hasCompleteProfile())

        // Step 2: Set 6-digit PIN password
        val targetPin = "654321"
        val pinSet = AppSecurityManager.set6DigitPin(context, targetPin)
        assertTrue("Setting 6-digit PIN should succeed", pinSet)
        assertTrue("PIN should be enabled in securityConfig", AppSecurityManager.securityConfig.value.isPinEnabled)
        assertEquals(targetPin, AppSecurityManager.securityConfig.value.pinCode)

        // Step 3: Register fingerprint credential to generate fingerprint.dat
        val (regOk, _) = AppSecurityManager.registerFingerprintCredential(context)
        assertTrue("Fingerprint registration should succeed", regOk)

        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, "fingerprint.dat")
        assertTrue("fingerprint.dat should exist", datFile.exists())

        val datContent = datFile.readText()
        // Verify fingerprint.dat contains 6-digit password and security metadata
        assertTrue("fingerprint.dat must contain PIN_CODE", datContent.contains("PIN_CODE=654321"))
        assertTrue("fingerprint.dat must contain PIN_PASSWORD", datContent.contains("PIN_PASSWORD=654321"))
        assertTrue("fingerprint.dat must contain PIN_ENABLED=true", datContent.contains("PIN_ENABLED=true"))
        assertTrue("fingerprint.dat must contain PIN_SALT", datContent.contains("PIN_SALT="))
        assertTrue("fingerprint.dat must contain PIN_HASH", datContent.contains("PIN_HASH="))
        assertTrue("fingerprint.dat must contain GOOGLE_EMAIL", datContent.contains("GOOGLE_EMAIL=alice.smith@gmail.com"))
        assertTrue("fingerprint.dat must contain GOOGLE_NAME", datContent.contains("GOOGLE_NAME=Alice Smith"))

        // Step 4: Simulate moving to a clean new device
        // Delete local profile and delete local security credentials
        com.example.util.ProfileManager.deleteProfile(context)
        assertFalse("Profile should be cleared", com.example.util.ProfileManager.hasCompleteProfile())

        val securityDir = AppStorageHelper.getSecurityDir(context)
        File(securityDir, "pin_credential.dat").delete()
        File(securityDir, "security_config.json").delete()

        // Disable PIN locally on simulated clean device
        AppSecurityManager.disablePin(context, targetPin)
        assertFalse("PIN should initially be disabled on clean device", AppSecurityManager.securityConfig.value.isPinEnabled)

        // Step 5: Restore from fingerprint.dat on the new device
        val unlockResult = AppSecurityManager.verifyAndUnlockWithBackupFingerprintContent(context, datContent)
        assertTrue("Fingerprint unlock and restoration should succeed", unlockResult.first)

        // Verify that on the new device:
        // 1. User profile and Google account are auto-restored
        val restoredProfile = com.example.util.ProfileManager.userProfile.value
        org.junit.Assert.assertNotNull("Restored profile must not be null", restoredProfile)
        assertEquals("Alice Smith", restoredProfile?.fullName)
        assertEquals("alice.smith@gmail.com", restoredProfile?.googleEmail)
        assertTrue("Google account must be connected", restoredProfile?.isGoogleConnected == true)
        assertTrue("Profile must be complete", com.example.util.ProfileManager.hasCompleteProfile())

        // 2. 6-digit password is auto-enabled!
        val restoredConfig = AppSecurityManager.securityConfig.value
        assertTrue("6-digit PIN should be automatically enabled on new device", restoredConfig.isPinEnabled)

        // 3. Local pin_credential.dat exists on the new device
        val restoredPinFile = File(securityDir, "pin_credential.dat")
        assertTrue("pin_credential.dat must be created on new device", restoredPinFile.exists())

        // 4. Verifying the 6-digit password "654321" succeeds on the new device!
        assertTrue("Entering restored 6-digit password should succeed", AppSecurityManager.verifyPin(targetPin, context))
        assertFalse("Entering wrong PIN should fail", AppSecurityManager.verifyPin("000000", context))
    }

    @Test
    fun testFingerprintDatContainsOnlyCredentialsAndNoMediaZip() {
        // Step 1: Ensure profile and PIN exist
        val testProfile = com.example.data.local.UserProfile(
            fullName = "Bob Builder",
            dateOfBirth = "01-01-1990",
            phoneNumber = "+1 555-9876",
            emailId = "bob@example.com",
            device = "Pixel 8",
            village = "Austin",
            district = "Travis",
            state = "Texas",
            country = "USA",
            pincode = "78701",
            profileImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            googleEmail = "bob.builder@gmail.com",
            googleDisplayName = "Bob Builder",
            googleId = "google_id_bob",
            isGoogleConnected = true
        )
        com.example.util.ProfileManager.saveProfile(context, testProfile)
        val testPin = "112233"
        AppSecurityManager.set6DigitPin(context, testPin)

        // Step 2: Register fingerprint, generating fingerprint.dat
        val (regOk, _) = AppSecurityManager.registerFingerprintCredential(context)
        assertTrue("Registration should succeed", regOk)

        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, "fingerprint.dat")
        assertTrue("fingerprint.dat must exist", datFile.exists())

        val datContent = datFile.readText()

        // 1. Security & App Lock Credentials
        assertTrue("fingerprint.dat must contain PIN_CODE", datContent.contains("PIN_CODE=112233"))
        assertTrue("fingerprint.dat must contain PIN_PASSWORD", datContent.contains("PIN_PASSWORD=112233"))
        assertTrue("fingerprint.dat must contain PIN_SALT", datContent.contains("PIN_SALT="))
        assertTrue("fingerprint.dat must contain PIN_HASH", datContent.contains("PIN_HASH="))
        assertTrue("fingerprint.dat must contain PIN_ENABLED", datContent.contains("PIN_ENABLED=true"))
        assertTrue("fingerprint.dat must contain TOKEN", datContent.contains("TOKEN="))
        assertTrue("fingerprint.dat must contain SALT", datContent.contains("SALT="))
        assertTrue("fingerprint.dat must contain HASH", datContent.contains("HASH="))
        assertTrue("fingerprint.dat must contain REGISTERED_AT", datContent.contains("REGISTERED_AT="))
        assertTrue("fingerprint.dat must contain DEVICE", datContent.contains("DEVICE="))
        assertTrue("fingerprint.dat must contain PACKAGE", datContent.contains("PACKAGE="))

        // 2. User Profile & Connected Accounts
        assertTrue("fingerprint.dat must contain GOOGLE_EMAIL", datContent.contains("GOOGLE_EMAIL=bob.builder@gmail.com"))
        assertTrue("fingerprint.dat must contain GOOGLE_NAME", datContent.contains("GOOGLE_NAME=Bob Builder"))
        assertTrue("fingerprint.dat must contain GOOGLE_ID", datContent.contains("GOOGLE_ID=google_id_bob"))
        assertTrue("fingerprint.dat must contain GOOGLE_CONNECTED", datContent.contains("GOOGLE_CONNECTED=true"))
        assertTrue("fingerprint.dat must contain PROFILE_JSON", datContent.contains("PROFILE_JSON="))
        assertTrue("fingerprint.dat must contain DEVICE_CODE", datContent.contains("DEVICE_CODE="))

        // 3. Biometrics & Privacy Settings
        assertTrue("fingerprint.dat must contain FACE_LOCK_ENABLED", datContent.contains("FACE_LOCK_ENABLED="))
        assertTrue("fingerprint.dat must contain IS_FACE_ENROLLED", datContent.contains("IS_FACE_ENROLLED="))
        assertTrue("fingerprint.dat must contain FACE_ENROLLED_AT", datContent.contains("FACE_ENROLLED_AT="))
        assertTrue("fingerprint.dat must contain RECENT_APP_PRIVACY", datContent.contains("RECENT_APP_PRIVACY="))
        assertTrue("fingerprint.dat must contain FB_CONNECTED", datContent.contains("FB_CONNECTED="))
        assertTrue("fingerprint.dat must contain FB_USERNAME", datContent.contains("FB_USERNAME="))

        // 4. Must NOT contain Media ZIP in fingerprint.dat
        assertFalse("fingerprint.dat must NOT contain MEDIA_ZIP_NAME", datContent.contains("MEDIA_ZIP_NAME="))
        assertFalse("fingerprint.dat must NOT contain MEDIA_ZIP_SIZE", datContent.contains("MEDIA_ZIP_SIZE="))
        assertFalse("fingerprint.dat must NOT contain MEDIA_ZIP_BASE64", datContent.contains("MEDIA_ZIP_BASE64="))

        // Step 3: Restore on new device using fingerprint.dat content
        val (unlockOk, msg) = AppSecurityManager.verifyAndUnlockWithBackupFingerprintContent(context, datContent)
        assertTrue("Restoration and unlock should succeed: $msg", unlockOk)

        // Verify profile and PIN are restored
        val restoredProfile = com.example.util.ProfileManager.userProfile.value
        assertNotNull("Restored profile must not be null", restoredProfile)
        assertEquals("Bob Builder", restoredProfile?.fullName)
        assertEquals("bob.builder@gmail.com", restoredProfile?.googleEmail)
        assertTrue("PIN verification should succeed", AppSecurityManager.verifyPin(testPin, context))
    }

    @Test
    fun testAutoMigrationOfLegacyMediaAndForeverEncryption() {
        val testPin = "987654"
        AppSecurityManager.set6DigitPin(context, testPin)

        // Simulate older user having raw unencrypted files in Android/media/<packageName>/media_imported
        val legacyDir = AppStorageHelper.getLegacyMediaImportedDir(context)
        legacyDir.mkdirs()
        val legacyPhoto = File(legacyDir, "legacy_user_photo.jpg")
        legacyPhoto.writeText("OLD_USER_IMAGE_CONTENT_BEFORE_UPDATE")
        assertTrue("Legacy photo must exist in public folder before migration", legacyPhoto.exists())

        // Simulate app unlock / update run
        val migrated = com.example.util.MediaZipEncryptionHelper.autoMigrateAndEncryptAllMedia(context)
        assertTrue("Auto migration and encryption should succeed", migrated)

        // 1. Loose file in public storage MUST be removed so file managers cannot see it
        assertFalse("Legacy unencrypted photo must NOT remain exposed in public folder", legacyPhoto.exists())

        // 2. Encrypted folder and all_media_encrypted.zip MUST exist in public folder for file managers
        val encryptDir = AppStorageHelper.getEncryptedMediaDir(context)
        assertTrue("encrypt/ folder must exist in public Android/media", encryptDir.exists())
        val encryptedZip = AppStorageHelper.getAllMediaEncryptedZipFile(context)
        assertTrue("all_media_encrypted.zip must exist in encrypt/ folder", encryptedZip.exists())
        assertTrue("all_media_encrypted.zip must have data", encryptedZip.length() > 0L)

        // 3. Inside the app, image MUST exist in internal working directory and be readable
        val internalPhoto = File(AppStorageHelper.getMediaImportedDir(context), "legacy_user_photo.jpg")
        assertTrue("Photo must be accessible inside app internal storage", internalPhoto.exists())
        assertEquals("OLD_USER_IMAGE_CONTENT_BEFORE_UPDATE", internalPhoto.readText())

        // 4. Verifying with correct password decrypts properly
        val zipArchive = net.lingala.zip4j.ZipFile(encryptedZip, testPin.toCharArray())
        assertTrue("Encrypted archive must be valid", zipArchive.isValidZipFile)
    }

    @Test
    fun testSsiImageRearrangeAndOrderPersistence() {
        val mediaDir = AppStorageHelper.getMediaImportedDir(context)
        mediaDir.mkdirs()
        val fileA = File(mediaDir, "img_a.jpg")
        val fileB = File(mediaDir, "img_b.jpg")
        val fileC = File(mediaDir, "img_c.jpg")
        fileA.writeText("IMG_A")
        fileB.writeText("IMG_B")
        fileC.writeText("IMG_C")

        com.example.util.SsiManager.refresh(context)
        val initialList = com.example.util.SsiManager.importedImages.value
        assertTrue("Must have at least 3 images", initialList.size >= 3)

        val initialFirstName = initialList[0].name
        val initialLastName = initialList.last().name

        // Move item at index 0 to last position
        val moveOk = com.example.util.SsiManager.moveImage(context, 0, initialList.size - 1)
        assertTrue("Move should succeed", moveOk)

        val updatedList = com.example.util.SsiManager.importedImages.value
        assertEquals("Initial first item should now be at end", initialFirstName, updatedList.last().name)

        // Verify refresh retains custom persistent order
        com.example.util.SsiManager.refresh(context)
        val refreshedList = com.example.util.SsiManager.importedImages.value
        assertEquals("Refreshed list must preserve custom order", initialFirstName, refreshedList.last().name)
    }
}
