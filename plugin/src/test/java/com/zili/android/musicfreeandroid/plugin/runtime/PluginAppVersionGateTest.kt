package com.zili.android.musicfreeandroid.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PluginAppVersionGate] — proves the semver-based appVersion
 * gate behaves the way the RN MusicFree pluginManager expects.
 *
 * Test branches:
 *  - null / blank constraint: pass (no requirement declared).
 *  - `>=` / `^` satisfied / violated.
 *  - Invalid constraint string → Failed(VersionNotMatch) so plugin authors
 *    notice the typo rather than silently bypassing the gate.
 */
class PluginAppVersionGateTest {
    private val gate = PluginAppVersionGate()

    @Test fun `null constraint passes`() {
        assertNull(gate.evaluate(constraint = null, appVersion = "1.0.0"))
    }

    @Test fun `blank constraint passes`() {
        assertNull(gate.evaluate(constraint = "  ", appVersion = "1.0.0"))
    }

    @Test fun `gte satisfied`() {
        assertNull(gate.evaluate(">=1.0.0", "1.2.3"))
    }

    @Test fun `gte violated`() {
        val result = gate.evaluate(">=2.0.0", "1.2.3")
        assertNotNull(result)
        assertEquals(PluginErrorReason.VersionNotMatch, result!!.reason)
        assertTrue(
            "detail should reference the requested constraint",
            result.detail!!.contains(">=2.0.0"),
        )
    }

    @Test fun `caret satisfied`() {
        assertNull(gate.evaluate("^1.2.0", "1.2.5"))
    }

    @Test fun `caret violated by major`() {
        val result = gate.evaluate("^1.2.0", "2.0.0")
        assertNotNull(result)
        assertEquals(PluginErrorReason.VersionNotMatch, result!!.reason)
    }

    @Test fun `invalid constraint yields VersionNotMatch`() {
        val result = gate.evaluate("not-a-version", "1.0.0")
        assertNotNull(result)
        assertEquals(PluginErrorReason.VersionNotMatch, result!!.reason)
        assertTrue(
            "detail should reference the offending input",
            result.detail!!.contains("not-a-version"),
        )
    }

    @Test fun `loose appVersion parses ok`() {
        // Android PackageInfo.versionName commonly drops the patch component
        // on debug builds. The gate should not fail loads on those builds.
        assertNull(gate.evaluate(">=1.0", "1.0"))
    }
}
