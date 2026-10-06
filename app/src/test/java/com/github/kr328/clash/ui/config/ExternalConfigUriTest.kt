package com.github.kr328.clash.ui.config

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalConfigUriTest {
    private val app = "app.ducker.android"

    @Test fun rejectsFileAndOtherSchemes() {
        for (scheme in listOf(null, "file", "https", "android.resource")) {
            assertFalse(externalConfigUriAllowed(scheme, "external.files", app))
        }
    }

    @Test fun rejectsMissingAuthority() {
        for (authority in listOf(null, "", " ", "0@")) {
            assertFalse(externalConfigUriAllowed("content", authority, app))
        }
    }

    @Test fun rejectsOwnProvidersIncludingUserQualifiedUris() {
        for (provider in listOf(app, "$app.files", "$app.logs", "$app.settings", "$app.status")) {
            assertFalse(externalConfigUriAllowed("content", provider, app))
            assertFalse(externalConfigUriAllowed("content", "0@${provider.uppercase()}", app))
        }
    }

    @Test fun acceptsFileManagerProviders() {
        assertTrue(externalConfigUriAllowed("content", "com.android.externalstorage.documents", app))
        assertTrue(externalConfigUriAllowed("content", "10@com.android.providers.downloads.documents", app))
    }
}
