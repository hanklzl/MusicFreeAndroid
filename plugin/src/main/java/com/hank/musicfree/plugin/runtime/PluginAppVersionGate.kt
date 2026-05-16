package com.hank.musicfree.plugin.runtime

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.satisfiedBy
import io.github.z4kn4fein.semver.constraints.toConstraint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates a plugin's declared `appVersion` semver constraint against the host
 * app's `versionName`. Used by [com.hank.musicfree.plugin.manager.PluginManager]
 * during install/load to refuse plugins that explicitly demand a higher (or
 * differently-shaped) host version.
 *
 * Convention mirrors RN MusicFree:
 *  - A plugin may set `appVersion` to a semver Constraint string such as
 *    `">=1.0.0"`, `"^1.2.0"`, `"~1.2.3"`, `"1.x"`, `">=1.0.0 <2.0.0"`.
 *  - Missing / blank constraints are interpreted as no requirement and pass.
 *  - Invalid constraints are treated as a permanent failure (not a silent pass)
 *    so plugin authors notice the typo rather than shipping broken metadata.
 *
 * @see io.github.z4kn4fein.semver.Version
 * @see io.github.z4kn4fein.semver.constraints.Constraint
 */
@Singleton
class PluginAppVersionGate @Inject constructor() {

    /**
     * Evaluate [constraint] (plugin-declared) against [appVersion] (host).
     *
     * Returns:
     *  - `null` when the constraint is satisfied (or absent).
     *  - [PluginState.Failed] with reason [PluginErrorReason.VersionNotMatch]
     *    when the constraint is non-empty but unsatisfied or unparseable.
     */
    fun evaluate(constraint: String?, appVersion: String): PluginState.Failed? {
        if (constraint.isNullOrBlank()) return null
        val parsedConstraint = try {
            constraint.toConstraint()
        } catch (e: Exception) {
            return PluginState.Failed(
                PluginErrorReason.VersionNotMatch,
                "constraint='$constraint' is not a valid semver constraint: ${e.message}",
            )
        }
        val parsedVersion = try {
            // `strict = false` permits non-canonical versionNames such as "1.0"
            // (without patch) — Android's PackageInfo.versionName routinely omits
            // patch on Debug builds, so we accept them rather than failing all
            // plugin loads on debug installs.
            Version.parse(appVersion, strict = false)
        } catch (e: Exception) {
            return PluginState.Failed(
                PluginErrorReason.VersionNotMatch,
                "appVersion='$appVersion' is not a parseable semver: ${e.message}",
            )
        }
        return if (parsedConstraint.satisfiedBy(parsedVersion)) {
            null
        } else {
            PluginState.Failed(
                PluginErrorReason.VersionNotMatch,
                "plugin requires $constraint, app is $appVersion",
            )
        }
    }
}
