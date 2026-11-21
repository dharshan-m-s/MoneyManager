package com.moneymanager.app.updater

import com.moneymanager.app.updater.data.BoundedRead
import com.moneymanager.app.updater.data.UpdateUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateUrlPolicyTest {

    @Test
    fun acceptsOnlyOfficialHttpsUpdateHosts() {
        assertTrue(UpdateUrlPolicy.isAllowedApiUrl("https://api.github.com/repos/a/b/releases"))
        assertTrue(
            UpdateUrlPolicy.isAllowedAssetUrl(
                "https://objects.githubusercontent.com/github-release/download?token=abc"
            )
        )
        assertTrue(
            UpdateUrlPolicy.isAllowedAssetUrl(
                "https://release-assets.githubusercontent.com/github-release/download?token=abc"
            )
        )
    }

    @Test
    fun rejectsUnsafeSchemesHostsAndCredentials() {
        assertFalse(UpdateUrlPolicy.isAllowedAssetUrl("http://github.com/app.apk"))
        assertFalse(UpdateUrlPolicy.isAllowedAssetUrl("https://evil.example/app.apk"))
        assertFalse(UpdateUrlPolicy.isAllowedAssetUrl("https://user:pass@github.com/app.apk"))
        assertFalse(UpdateUrlPolicy.isAllowedAssetUrl("https://github.com/app.apk#frag"))
        assertFalse(UpdateUrlPolicy.isAllowedApiUrl("https://github.com/repos/a/b/releases"))
        assertFalse(UpdateUrlPolicy.isAllowedAssetUrl(null))
    }

    @Test
    fun releasePageUrlIsBuiltFromValidatedTag() {
        assertEquals(
            "https://github.com/dharshan-m-s/MoneyManager/releases/tag/v1.2.3",
            UpdateUrlPolicy.releasePageUrl("dharshan-m-s", "MoneyManager", "v1.2.3"),
        )
        assertNull(UpdateUrlPolicy.releasePageUrl("dharshan-m-s", "MoneyManager", "../evil"))
    }

    @Test
    fun boundedReadRejectsOversizedBodies() {
        assertEquals("abc", BoundedRead.readText(ByteArrayInputStream("abc".toByteArray()), 8))
        assertNull(BoundedRead.readText(ByteArrayInputStream("abcdefghij".toByteArray()), 4))
    }
}
