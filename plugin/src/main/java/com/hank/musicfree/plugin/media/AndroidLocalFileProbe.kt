package com.hank.musicfree.plugin.media

import android.content.Context
import android.net.Uri
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContentResolver-backed implementation of [LocalFileProbe].
 *
 * Resolution strategy:
 * - If the URI has no scheme and starts with `/`, treat it as a plain file path
 *   and delegate to [File.canRead].
 * - Otherwise, attempt to open the URI via [android.content.ContentResolver]
 *   in read mode. A successful open (and immediate close) means the file is readable.
 *
 * Exceptions are handled by severity:
 * - [SecurityException] (permission/access denied — degraded path) is logged at **error** level.
 * - Other throwables (e.g. [java.io.FileNotFoundException] — file genuinely missing) are logged
 *   at detail level. The default return value is `false` in both cases.
 */
@Singleton
class AndroidLocalFileProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalFileProbe {

    override fun isReadable(uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == null && uri.startsWith("/")) {
            return@runCatching File(uri).canRead()
        }
        context.contentResolver.openFileDescriptor(parsed, "r")?.use { true } == true
    }.getOrElse { throwable ->
        when (throwable) {
            is SecurityException -> MfLog.error(
                category = LogCategory.PLUGIN,
                event = "local_file_probe_failed",
                throwable = throwable,
                fields = mapOf("uri" to uri, "reason" to "security_exception"),
            )
            else -> MfLog.detail(
                category = LogCategory.PLUGIN,
                event = "local_file_probe_failed",
                fields = mapOf("uri" to uri, "reason" to (throwable.javaClass.simpleName ?: "exception")),
            )
        }
        false
    }
}
