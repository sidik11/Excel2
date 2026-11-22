package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ApsPeerDevice
import com.example.util.ApsReceiverVaultTarget
import com.example.util.ApsTransferEngine
import com.example.util.ApsTransferItem
import com.example.util.ApsTransportType
import com.example.util.ApsVaultType
import com.example.util.ImgVaultEngine
import com.example.util.ImgVaultResult
import com.example.util.VidVaultEngine
import com.example.util.VidVaultResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApsTransferEngineTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        testDir = File(context.cacheDir, "aps_transfer_tests").apply {
            deleteRecursively()
            mkdirs()
        }
        ApsTransferEngine.stopReceiveMode()
        ApsTransferEngine.stopDiscovery()
        ApsTransferEngine.testAutoApproveIncoming = true
    }

    @After
    fun tearDown() {
        ApsTransferEngine.testAutoApproveIncoming = null
        ApsTransferEngine.stopReceiveMode()
        ApsTransferEngine.stopDiscovery()
        testDir.deleteRecursively()
    }

    @Test
    fun testVidVaultToVidVaultDirectTransfer(): Unit = runBlocking {
        // 1. Create Sender & Receiver VID Vaults
        val senderVault = File(testDir, "sender.vidvault")
        val receiverVault = File(testDir, "receiver.vidvault")

        VidVaultEngine.createVault(
            context = context,
            targetFile = senderVault,
            vaultName = "Sender VID",
            password = "SenderPass#1",
            recoveryKey = VidVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = VidVaultEngine.CAPACITY_30GB
        )
        VidVaultEngine.createVault(
            context = context,
            targetFile = receiverVault,
            vaultName = "Receiver VID",
            password = "ReceiverPass#2",
            recoveryKey = VidVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = VidVaultEngine.CAPACITY_30GB
        )

        val (senderKey, _) = (VidVaultEngine.openVault(context, senderVault, "SenderPass#1") as VidVaultResult.Success).data
        val (receiverKey, receiverMeta) = (VidVaultEngine.openVault(context, receiverVault, "ReceiverPass#2") as VidVaultResult.Success).data

        // 2. Import a >128 KB binary video payload into Sender VID Vault
        val videoPayload = ByteArray(140_513) { i -> ((i * 29 + 13) and 0xFF).toByte() }
        val sourceVideoFile = File(testDir, "secret_clip.mp4").apply { writeBytes(videoPayload) }
        val importRes = VidVaultEngine.importVideoFromFile(
            context = context,
            vaultFile = senderVault,
            masterKey = senderKey,
            sourceFile = sourceVideoFile,
            displayName = "secret_clip.mp4",
            mimeType = "video/mp4",
            durationHintMs = 12_500L
        )
        assertTrue("Sender video import must succeed", importRes is VidVaultResult.Success)
        val senderMeta = VidVaultEngine.readMetadata(senderVault, senderKey)!!
        val senderRec = senderMeta.activeRecords.first()

        // 3. Start Receiver APS Mode strictly for VID_VAULT
        ApsTransferEngine.startReceiveMode(
            context = context,
            target = ApsReceiverVaultTarget(
                vaultType = ApsVaultType.VID_VAULT,
                vaultFile = receiverVault,
                masterKey = receiverKey,
                targetFolderId = receiverMeta.folders.firstOrNull()?.id,
                onIngestComplete = {}
            )
        )
        val tcpPort = ApsTransferEngine.state.value.localTcpPort
        assertTrue("APS TCP server port must be bound", tcpPort > 0)

        // 4. Execute direct Vault-to-Vault APS send over loopback TCP
        val loopbackPeer = ApsPeerDevice(
            idKey = "LOOPBACK_VID",
            deviceId = "TEST-RCV-VID",
            profileName = "Receiver Phone",
            deviceModel = "Test Device",
            vaultType = ApsVaultType.VID_VAULT,
            transport = ApsTransportType.WIFI_LAN,
            hostAddress = "127.0.0.1",
            port = tcpPort
        )
        val item = ApsTransferItem(
            id = senderRec.id,
            name = senderRec.name,
            sizeBytes = senderRec.sizeBytes,
            mimeType = senderRec.mimeType,
            vaultType = ApsVaultType.VID_VAULT,
            durationMs = senderRec.durationMs,
            streamPlaintextTo = { out ->
                VidVaultEngine.streamDecryptVideoToOutputStream(senderVault, senderKey, senderRec, out) is VidVaultResult.Success
            }
        )

        val (ok, msg) = ApsTransferEngine.executeSendToPeerSuspend(
            context = context,
            peer = loopbackPeer,
            vaultType = ApsVaultType.VID_VAULT,
            items = listOf(item)
        )
        assertTrue("VID->VID APS transfer must succeed, got: $msg", ok)

        // 5. Verify Receiver VID Vault now has the video and bytes match identically!
        delay(100L)
        val updatedReceiverMeta = VidVaultEngine.readMetadata(receiverVault, receiverKey)
        assertNotNull(updatedReceiverMeta)
        assertEquals(1, updatedReceiverMeta!!.activeVideoCount)
        val receivedRec = updatedReceiverMeta.activeRecords.first()
        assertEquals("secret_clip.mp4", receivedRec.name)

        val decryptedOut = ByteArrayOutputStream()
        val decOk = VidVaultEngine.streamDecryptVideoToOutputStream(receiverVault, receiverKey, receivedRec, decryptedOut) is VidVaultResult.Success
        assertTrue(decOk)
        assertArrayEquals(videoPayload, decryptedOut.toByteArray())
    }

    @Test
    fun testImgVaultToImgVaultDirectTransfer(): Unit = runBlocking {
        // 1. Create Sender & Receiver IMG Vaults
        val senderVault = File(testDir, "sender.vault")
        val receiverVault = File(testDir, "receiver.vault")

        ImgVaultEngine.createVault(
            context = context,
            targetFile = senderVault,
            vaultName = "Sender IMG",
            password = "ImgPass#1",
            recoveryKey = ImgVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = ImgVaultEngine.CAPACITY_5GB
        )
        ImgVaultEngine.createVault(
            context = context,
            targetFile = receiverVault,
            vaultName = "Receiver IMG",
            password = "ImgPass#2",
            recoveryKey = ImgVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = ImgVaultEngine.CAPACITY_5GB
        )

        val (senderKey, _) = (ImgVaultEngine.openVault(context, senderVault, "ImgPass#1") as ImgVaultResult.Success).data
        val (receiverKey, receiverMeta) = (ImgVaultEngine.openVault(context, receiverVault, "ImgPass#2") as ImgVaultResult.Success).data

        // 2. Import binary image payload into Sender IMG Vault
        val imagePayload = ByteArray(81_927) { i -> ((i * 43 + 5) and 0xFF).toByte() }
        val sourceImgFile = File(testDir, "private_photo.jpg").apply { writeBytes(imagePayload) }
        val importRes = ImgVaultEngine.importImageFromFile(
            context = context,
            vaultFile = senderVault,
            masterKey = senderKey,
            sourceFile = sourceImgFile,
            displayName = "private_photo.jpg",
            mimeType = "image/jpeg"
        )
        assertTrue("Sender image import must succeed", importRes is ImgVaultResult.Success)
        val senderMeta = ImgVaultEngine.readMetadata(senderVault, senderKey)!!
        val senderRec = senderMeta.activeRecords.first()

        // 3. Start Receiver APS Mode strictly for IMG_VAULT
        ApsTransferEngine.startReceiveMode(
            context = context,
            target = ApsReceiverVaultTarget(
                vaultType = ApsVaultType.IMG_VAULT,
                vaultFile = receiverVault,
                masterKey = receiverKey,
                targetFolderId = receiverMeta.folders.firstOrNull()?.id,
                onIngestComplete = {}
            )
        )
        val tcpPort = ApsTransferEngine.state.value.localTcpPort
        assertTrue(tcpPort > 0)

        // 4. Send IMG -> IMG over loopback TCP
        val loopbackPeer = ApsPeerDevice(
            idKey = "LOOPBACK_IMG",
            deviceId = "TEST-RCV-IMG",
            profileName = "Receiver IMG Phone",
            deviceModel = "Test Device",
            vaultType = ApsVaultType.IMG_VAULT,
            transport = ApsTransportType.WIFI_LAN,
            hostAddress = "127.0.0.1",
            port = tcpPort
        )
        val item = ApsTransferItem(
            id = senderRec.id,
            name = senderRec.name,
            sizeBytes = senderRec.sizeBytes,
            mimeType = senderRec.mimeType,
            vaultType = ApsVaultType.IMG_VAULT,
            streamPlaintextTo = { out ->
                val dec = ImgVaultEngine.decryptImageRecord(context, senderVault, senderKey, senderRec)
                if (dec is ImgVaultResult.Success) {
                    val f = dec.data
                    try {
                        FileInputStream(f).use { fis ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = fis.read(buf)
                                if (r <= 0) break
                                out.write(buf, 0, r)
                            }
                        }
                        true
                    } finally {
                        try { f.delete() } catch (_: Throwable) {}
                    }
                } else {
                    false
                }
            }
        )

        val (ok, msg) = ApsTransferEngine.executeSendToPeerSuspend(
            context = context,
            peer = loopbackPeer,
            vaultType = ApsVaultType.IMG_VAULT,
            items = listOf(item)
        )
        assertTrue("IMG->IMG APS transfer must succeed, got: $msg", ok)

        // 5. Verify Receiver IMG Vault received exact image bytes!
        delay(100L)
        val updatedReceiverMeta = ImgVaultEngine.readMetadata(receiverVault, receiverKey)
        assertNotNull(updatedReceiverMeta)
        assertEquals(1, updatedReceiverMeta!!.activeImageCount)
        val receivedRec = updatedReceiverMeta.activeRecords.first()
        assertEquals("private_photo.jpg", receivedRec.name)

        val decRes = ImgVaultEngine.decryptImageRecord(context, receiverVault, receiverKey, receivedRec)
        assertTrue(decRes is ImgVaultResult.Success)
        val decFile = (decRes as ImgVaultResult.Success).data
        assertArrayEquals(imagePayload, decFile.readBytes())
        decFile.delete()
        Unit
    }

    @Test
    fun testVidVaultSenderToImgVaultReceiverMustFailConnection(): Unit = runBlocking {
        // Receiver has IMG Vault APS enabled!
        val receiverImgVault = File(testDir, "receiver_only_img.vault")
        ImgVaultEngine.createVault(
            context = context,
            targetFile = receiverImgVault,
            vaultName = "Receiver IMG Only",
            password = "ImgOnly#1",
            recoveryKey = ImgVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = ImgVaultEngine.CAPACITY_5GB
        )
        val (receiverKey, receiverMeta) = (ImgVaultEngine.openVault(context, receiverImgVault, "ImgOnly#1") as ImgVaultResult.Success).data

        ApsTransferEngine.startReceiveMode(
            context = context,
            target = ApsReceiverVaultTarget(
                vaultType = ApsVaultType.IMG_VAULT,
                vaultFile = receiverImgVault,
                masterKey = receiverKey,
                targetFolderId = receiverMeta.folders.firstOrNull()?.id,
                onIngestComplete = {}
            )
        )
        val tcpPort = ApsTransferEngine.state.value.localTcpPort
        assertTrue(tcpPort > 0)

        // Sender tries to send VID_VAULT file with vaultType=null on peer so the REAL wire handshake executes!
        val manualPeer = ApsPeerDevice(
            idKey = "MANUAL_IMG_RCV",
            deviceId = "127.0.0.1:$tcpPort",
            profileName = "Receiver Running IMG APS",
            deviceModel = "Direct IP",
            vaultType = null,
            transport = ApsTransportType.WIFI_LAN,
            hostAddress = "127.0.0.1",
            port = tcpPort
        )
        val fakeVideoItem = ApsTransferItem(
            id = "vid-1",
            name = "movie.mp4",
            sizeBytes = 1024L,
            mimeType = "video/mp4",
            vaultType = ApsVaultType.VID_VAULT,
            streamPlaintextTo = { out ->
                out.write(ByteArray(1024) { 0x5A })
                true
            }
        )

        val (ok, errMsg) = ApsTransferEngine.executeSendToPeerSuspend(
            context = context,
            peer = manualPeer,
            vaultType = ApsVaultType.VID_VAULT,
            items = listOf(fakeVideoItem)
        )

        assertFalse("VID Vault -> IMG Vault receiver MUST FAIL!", ok)
        assertTrue(
            "Error message must explain vault mismatch, got: $errMsg",
            errMsg.contains("VID Vault", ignoreCase = true) || errMsg.contains("IMG Vault", ignoreCase = true)
        )

        // Verify Receiver IMG Vault was NOT polluted (still 0 images)
        val afterMeta = ImgVaultEngine.readMetadata(receiverImgVault, receiverKey)!!
        assertEquals(0, afterMeta.activeImageCount)
    }

    @Test
    fun testImgVaultSenderToVidVaultReceiverMustFailConnection(): Unit = runBlocking {
        // Receiver has VID Vault APS enabled!
        val receiverVidVault = File(testDir, "receiver_only_vid.vidvault")
        VidVaultEngine.createVault(
            context = context,
            targetFile = receiverVidVault,
            vaultName = "Receiver VID Only",
            password = "VidOnly#1",
            recoveryKey = VidVaultEngine.generateRecoveryKey(),
            maxCapacityBytes = VidVaultEngine.CAPACITY_30GB
        )
        val (receiverKey, receiverMeta) = (VidVaultEngine.openVault(context, receiverVidVault, "VidOnly#1") as VidVaultResult.Success).data

        ApsTransferEngine.startReceiveMode(
            context = context,
            target = ApsReceiverVaultTarget(
                vaultType = ApsVaultType.VID_VAULT,
                vaultFile = receiverVidVault,
                masterKey = receiverKey,
                targetFolderId = receiverMeta.folders.firstOrNull()?.id,
                onIngestComplete = {}
            )
        )
        val tcpPort = ApsTransferEngine.state.value.localTcpPort
        assertTrue(tcpPort > 0)

        // Sender tries to send IMG_VAULT file with vaultType=null so wire handshake validates it!
        val manualPeer = ApsPeerDevice(
            idKey = "MANUAL_VID_RCV",
            deviceId = "127.0.0.1:$tcpPort",
            profileName = "Receiver Running VID APS",
            deviceModel = "Direct IP",
            vaultType = null,
            transport = ApsTransportType.WIFI_LAN,
            hostAddress = "127.0.0.1",
            port = tcpPort
        )
        val fakeImgItem = ApsTransferItem(
            id = "img-1",
            name = "photo.jpg",
            sizeBytes = 512L,
            mimeType = "image/jpeg",
            vaultType = ApsVaultType.IMG_VAULT,
            streamPlaintextTo = { out ->
                out.write(ByteArray(512) { 0x3C })
                true
            }
        )

        val (ok, errMsg) = ApsTransferEngine.executeSendToPeerSuspend(
            context = context,
            peer = manualPeer,
            vaultType = ApsVaultType.IMG_VAULT,
            items = listOf(fakeImgItem)
        )

        assertFalse("IMG Vault -> VID Vault receiver MUST FAIL!", ok)
        assertTrue(
            "Error message must explain vault mismatch, got: $errMsg",
            errMsg.contains("IMG Vault", ignoreCase = true) || errMsg.contains("VID Vault", ignoreCase = true)
        )

        // Verify Receiver VID Vault was NOT polluted (still 0 videos)
        val afterMeta = VidVaultEngine.readMetadata(receiverVidVault, receiverKey)!!
        assertEquals(0, afterMeta.activeVideoCount)
    }
}
