package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ImgVaultEngine
import com.example.util.ImgVaultResult
import kotlinx.coroutines.runBlocking
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
class ImgVaultEngineTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        testDir = File(context.cacheDir, "vault_tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun testCreateAndUnlockVaultWithPassword() = runBlocking {
        val vaultFile = File(testDir, "test_gallery.vault")
        val password = "SuperSecretPassword123"
        val recoveryKey = ImgVaultEngine.generateRecoveryKey()

        // 1. Create Vault
        val createResult = ImgVaultEngine.createVault(
            context = context,
            targetFile = vaultFile,
            vaultName = "Test Gallery",
            password = password,
            recoveryKey = recoveryKey,
            maxCapacityBytes = ImgVaultEngine.CAPACITY_5GB
        )
        assertTrue("Vault creation must succeed", createResult is ImgVaultResult.Success)
        assertTrue("Vault file must exist", vaultFile.exists())
        assertTrue("Vault file must be initialized", vaultFile.length() > 256L)

        // 2. Open with correct password
        val openResult = ImgVaultEngine.openVault(context, vaultFile, password)
        assertTrue("Opening with correct password must succeed", openResult is ImgVaultResult.Success)
        val (key, metadata) = (openResult as ImgVaultResult.Success).data
        assertNotNull("Master key must not be null", key)
        assertEquals("Vault name must match", "Test Gallery", metadata.name)
        assertEquals("Max capacity must be 5 GB", ImgVaultEngine.CAPACITY_5GB, metadata.maxCapacityBytes)
        assertEquals("Should have 1 default folder", 1, metadata.folders.size)

        // 3. Open with wrong password must fail
        val wrongResult = ImgVaultEngine.openVault(context, vaultFile, "WrongPassword")
        assertTrue("Opening with wrong password must fail", wrongResult is ImgVaultResult.Error)

        // 4. Open with recovery key must succeed
        val recoveryResult = ImgVaultEngine.openVault(context, vaultFile, recoveryKey)
        assertTrue("Opening with recovery key must succeed", recoveryResult is ImgVaultResult.Success)
    }

    @Test
    fun testLogicalDeletionAndCompaction() = runBlocking {
        val vaultFile = File(testDir, "compaction_test.vault")
        val password = "Password987"
        val recoveryKey = ImgVaultEngine.generateRecoveryKey()

        ImgVaultEngine.createVault(
            context = context,
            targetFile = vaultFile,
            vaultName = "Compaction Test",
            password = password,
            recoveryKey = recoveryKey,
            maxCapacityBytes = ImgVaultEngine.CAPACITY_5GB
        )

        val openRes = ImgVaultEngine.openVault(context, vaultFile, password) as ImgVaultResult.Success
        val (key, _) = openRes.data

        // Create a new folder
        val folderRes = ImgVaultEngine.createFolder(vaultFile, key, "Vacation 2026")
        assertTrue("Folder creation must succeed", folderRes is ImgVaultResult.Success)
        val updatedMeta = (folderRes as ImgVaultResult.Success).data
        assertEquals(2, updatedMeta.folders.size)
        assertEquals("Vacation 2026", updatedMeta.folders[1].name)
    }

    @Test
    fun testBatchImportImages() = runBlocking {
        val vaultFile = File(testDir, "batch_import_test.vault")
        val password = "BatchPassword123"
        val recoveryKey = ImgVaultEngine.generateRecoveryKey()

        ImgVaultEngine.createVault(
            context = context,
            targetFile = vaultFile,
            vaultName = "Batch Vault",
            password = password,
            recoveryKey = recoveryKey
        )

        val openRes = ImgVaultEngine.openVault(context, vaultFile, password) as ImgVaultResult.Success
        val (key, _) = openRes.data

        // Create 5 dummy test image files
        val sampleUris = (1..5).map { i ->
            val imgFile = File(testDir, "sample_$i.jpg").apply {
                writeBytes(ByteArray(1024) { 0x42 })
            }
            android.net.Uri.fromFile(imgFile)
        }

        var progressUpdates = 0
        val batchRes = ImgVaultEngine.importImagesBatch(
            context = context,
            vaultFile = vaultFile,
            masterKey = key,
            sourceUris = sampleUris,
            onProgress = { current, total, _ ->
                progressUpdates++
                assertTrue(current <= total)
            }
        )

        assertTrue("Batch import must succeed", batchRes is ImgVaultResult.Success)
        val (successCount, attempted) = (batchRes as ImgVaultResult.Success).data
        assertEquals(5, successCount)
        assertEquals(5, attempted)
        assertTrue(progressUpdates > 0)

        // Verify reopened index has all 5 records
        val reopenRes = ImgVaultEngine.openVault(context, vaultFile, password) as ImgVaultResult.Success
        val (_, reloadedMeta) = reopenRes.data
        assertEquals(5, reloadedMeta.records.size)
    }
}
