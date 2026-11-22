package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.AppStorageHelper
import com.example.util.VidVaultEngine
import com.example.util.VidVaultResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VidVaultEngineTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        testDir = File(context.cacheDir, "vid_vault_tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun testVidVaultSharesSameLocationAsImgVault() {
        val imgDir = AppStorageHelper.getVaultStorageDir(context)
        val vidDir = AppStorageHelper.getVidVaultDir(context)
        assertEquals(imgDir.absolutePath, vidDir.absolutePath)
    }

    @Test
    fun testCreate30GbVaultAndUnlockWithPasswordAndRecoveryKey() = runBlocking {
        val vaultFile = File(testDir, "movies_30gb.vidvault")
        val password = "VideoSecretPassword#2026"
        val recoveryKey = VidVaultEngine.generateRecoveryKey()

        val createRes = VidVaultEngine.createVault(
            context = context,
            targetFile = vaultFile,
            vaultName = "Private Cinema",
            password = password,
            recoveryKey = recoveryKey,
            maxCapacityBytes = VidVaultEngine.CAPACITY_30GB
        )
        assertTrue("VID Vault creation must succeed", createRes is VidVaultResult.Success)
        assertTrue(vaultFile.exists() && vaultFile.length() > 256L)

        val openRes = VidVaultEngine.openVault(context, vaultFile, password)
        assertTrue("Opening with password must succeed", openRes is VidVaultResult.Success)
        val (key, meta) = (openRes as VidVaultResult.Success).data
        assertNotNull(key)
        assertEquals("Private Cinema", meta.name)
        assertEquals(VidVaultEngine.CAPACITY_30GB, meta.maxCapacityBytes)
        assertEquals(1, meta.folders.size)

        val wrongRes = VidVaultEngine.openVault(context, vaultFile, "WrongPass")
        assertTrue("Wrong password must fail", wrongRes is VidVaultResult.Error)

        val recRes = VidVaultEngine.openVault(context, vaultFile, recoveryKey)
        assertTrue("Recovery key unlock must succeed", recRes is VidVaultResult.Success)
    }

    @Test
    fun testStreamingImportDirectDiskMediaDataSourceAndCompaction() = runBlocking {
        val vaultFile = File(testDir, "stream_test.vidvault")
        val password = "StreamPass123"
        val recoveryKey = VidVaultEngine.generateRecoveryKey()

        VidVaultEngine.createVault(
            context = context,
            targetFile = vaultFile,
            vaultName = "Stream Vault",
            password = password,
            recoveryKey = recoveryKey,
            maxCapacityBytes = VidVaultEngine.CAPACITY_30GB
        )

        val (key, _) = (VidVaultEngine.openVault(context, vaultFile, password) as VidVaultResult.Success).data

        // Create two deterministic binary payloads (> 64 KB so multiple streaming chunks & AES-CTR blocks are exercised)
        val payloadA = ByteArray(150_003) { i -> ((i * 31 + 7) and 0xFF).toByte() }
        val payloadB = ByteArray(95_019) { i -> ((i * 17 + 93) and 0xFF).toByte() }

        val fileA = File(testDir, "clip_a.mkv").apply { writeBytes(payloadA) }
        val fileB = File(testDir, "clip_b.mp4").apply { writeBytes(payloadB) }

        val importRes = VidVaultEngine.importVideosBatch(
            context = context,
            vaultFile = vaultFile,
            masterKey = key,
            sourceUris = listOf(Uri.fromFile(fileA), Uri.fromFile(fileB))
        )
        assertTrue("Import must succeed", importRes is VidVaultResult.Success)
        val (imported, attempted) = (importRes as VidVaultResult.Success).data
        assertEquals(2, imported)
        assertEquals(2, attempted)

        val meta = VidVaultEngine.readMetadata(vaultFile, key)!!
        assertEquals(2, meta.activeVideoCount)
        val recA = meta.activeRecords[0]
        val recB = meta.activeRecords[1]

        // 1. Verify Direct-from-Disk EncryptedVideoDataSource random-access read & unaligned seek!
        val ds = VidVaultEngine.createDirectDiskMediaDataSource(vaultFile, key, recA)
        assertNotNull(ds)
        ds!!.use { source ->
            assertEquals(payloadA.size.toLong(), source.size)

            // Read at unaligned offset spanning multiple 16-byte AES blocks
            val unalignedOffset = 100_005L
            val sliceLen = 25_000
            val actualSlice = ByteArray(sliceLen)
            val readCount = source.readAt(unalignedOffset, actualSlice, 0, sliceLen)
            assertEquals(sliceLen, readCount)
            val expectedSlice = payloadA.copyOfRange(unalignedOffset.toInt(), unalignedOffset.toInt() + sliceLen)
            assertArrayEquals("Random-access AES-CTR slice must match plaintext exactly", expectedSlice, actualSlice)

            // Read tail bytes past EOF boundary
            val tailOffset = (payloadA.size - 19).toLong()
            val tailBuf = ByteArray(64)
            val tailRead = source.readAt(tailOffset, tailBuf, 0, 64)
            assertEquals(19, tailRead)
            assertArrayEquals(
                payloadA.copyOfRange(payloadA.size - 19, payloadA.size),
                tailBuf.copyOfRange(0, 19)
            )
        }

        // 2. Verify zero-RAM streaming export to OutputStream matches byte-for-byte
        val exportedBytes = ByteArrayOutputStream()
        val exportRes = VidVaultEngine.streamDecryptVideoToOutputStream(vaultFile, key, recB, exportedBytes)
        assertTrue(exportRes is VidVaultResult.Success)
        assertArrayEquals(payloadB, exportedBytes.toByteArray())

        // 3. Verify rename, markDeleted on recA, and streaming compaction reclaims space while keeping recB intact
        val renameRes = VidVaultEngine.renameVideo(vaultFile, key, recB.id, "favorite_movie.mp4")
        assertTrue(renameRes is VidVaultResult.Success)

        val sizeBeforeDelete = vaultFile.length()
        val delRes = VidVaultEngine.markDeleted(vaultFile, key, setOf(recA.id))
        assertTrue(delRes is VidVaultResult.Success)

        val compactRes = VidVaultEngine.compactVault(context, vaultFile, key)
        assertTrue(compactRes is VidVaultResult.Success)
        val compactedMeta = (compactRes as VidVaultResult.Success).data
        assertEquals(1, compactedMeta.activeVideoCount)
        assertEquals("favorite_movie.mp4", compactedMeta.activeRecords[0].name)
        assertTrue("Compacted container should shrink on disk", vaultFile.length() < sizeBeforeDelete)

        // Verify recB decrypts identically after compaction
        val postCompactOut = ByteArrayOutputStream()
        val postExport = VidVaultEngine.streamDecryptVideoToOutputStream(
            vaultFile,
            key,
            compactedMeta.activeRecords[0],
            postCompactOut
        )
        assertTrue(postExport is VidVaultResult.Success)
        assertArrayEquals(payloadB, postCompactOut.toByteArray())
    }
}
