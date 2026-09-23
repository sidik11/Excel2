package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.VaultManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Excel & Image Vault", appName)
  }

  @Test
  fun `test vault encrypt and decrypt cycle`() = runBlocking {
    val sampleText = "Secure payload data for vault testing 12345"
    val pin = "1234"
    val encrypted = VaultManager.encryptData(sampleText.toByteArray(Charsets.UTF_8), pin)
    val decrypted = VaultManager.decryptData(encrypted, pin)
    assertEquals(sampleText, String(decrypted, Charsets.UTF_8))
  }

  @Test
  fun `test wrong pin fails decryption`(): Unit = runBlocking {
    val sampleText = "Secret image data"
    val correctPin = "9876"
    val wrongPin = "0000"
    val encrypted = VaultManager.encryptData(sampleText.toByteArray(Charsets.UTF_8), correctPin)
    assertThrows(Exception::class.java) {
      runBlocking {
        VaultManager.decryptData(encrypted, wrongPin)
      }
    }
  }
}
