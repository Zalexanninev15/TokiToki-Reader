package io.github.zalexanninev15.tokitoki.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Shared Context helpers.
 *
 * These lived as private functions inside FeedScreen, which meant every other screen that
 * wanted to open a link or copy one had to grow its own copy — and a private top-level
 * function is invisible outside its file, so the second caller simply does not compile.
 */
fun Context.openInCustomTab(url: String) {
    // Some devices have no browser able to handle Custom Tabs; failing to open a link is
    // not worth crashing the screen the user is on.
    runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, url.toUri())
    }
}

fun Context.copyToClipboard(text: String, label: String = "post") {
    runCatching {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/**
 * Opens the system share sheet.
 *
 * The body is deliberately "who said it: what they said" followed by the link on its own
 * line — that is what lands legibly in a chat, and it is what the official Mastodon
 * client sends. A post without a public URL still shares its text rather than nothing.
 */
fun Context.sharePost(authorName: String, text: String, url: String?) {
    val body = buildString {
        append(authorName)
        if (text.isNotBlank()) {
            append(": ")
            append(text)
        }
        if (!url.isNullOrBlank()) {
            append("\n")
            append(url)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_SUBJECT, authorName)
    }
    runCatching { startActivity(Intent.createChooser(intent, null)) }
}
