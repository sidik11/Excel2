package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ImgVaultEngine
import com.example.util.ImgVaultResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
  fun `test img vault create and open cycle`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val targetFile = File(context.cacheDir, "sample_test.vault").apply { delete() }
    val password = "TestPassword456"
    val recoveryKey = ImgVaultEngine.generateRecoveryKey()

    val createRes = ImgVaultEngine.createVault(
      context = context,
      targetFile = targetFile,
      vaultName = "Sample Vault",
      password = password,
      recoveryKey = recoveryKey
    )
    assertTrue("Create vault must succeed", createRes is ImgVaultResult.Success)

    val openRes = ImgVaultEngine.openVault(context, targetFile, password)
    assertTrue("Open vault with correct password must succeed", openRes is ImgVaultResult.Success)
    val (key, metadata) = (openRes as ImgVaultResult.Success).data
    assertNotNull("Master key must not be null", key)
    assertEquals("Sample Vault", metadata.name)
  }

  @Test
  fun `test wrong password fails img vault opening`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val targetFile = File(context.cacheDir, "sample_wrong_test.vault").apply { delete() }
    val correctPassword = "CorrectPassword789"
    val wrongPassword = "WrongPassword000"
    val recoveryKey = ImgVaultEngine.generateRecoveryKey()

    ImgVaultEngine.createVault(
      context = context,
      targetFile = targetFile,
      vaultName = "Wrong Test Vault",
      password = correctPassword,
      recoveryKey = recoveryKey
    )

    val openRes = ImgVaultEngine.openVault(context, targetFile, wrongPassword)
    assertTrue("Opening with wrong password must return Error", openRes is ImgVaultResult.Error)
  }
}
