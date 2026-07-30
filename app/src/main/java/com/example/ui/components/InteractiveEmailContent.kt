package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.style.URLSpan
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

data class ExtractedEmailLink(
    val url: String,
    val label: String,
    val startIndex: Int,
    val endIndex: Int
)

fun openExternalUrl(context: Context, rawUrl: String) {
    try {
        val formattedUrl = when {
            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
            rawUrl.startsWith("www.", ignoreCase = true) -> "https://$rawUrl"
            else -> "https://$rawUrl"
        }
        val uri = Uri.parse(formattedUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun parseMessageContent(
    bodyText: String,
    bodyHtml: String?,
    linkColor: Color = Color(0xFF38BDF8)
): Pair<AnnotatedString, List<String>> {
    val rawText: String
    val links = mutableListOf<ExtractedEmailLink>()

    // If HTML is present, extract HTML links using HtmlCompat
    if (!bodyHtml.isNullOrBlank()) {
        val spanned = HtmlCompat.fromHtml(bodyHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        rawText = spanned.toString()
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)

        for (span in urlSpans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            val spanUrl = span.url
            if (start >= 0 && end > start && !spanUrl.isNullOrBlank()) {
                val label = if (start < rawText.length && end <= rawText.length) {
                    rawText.substring(start, end)
                } else spanUrl
                links.add(ExtractedEmailLink(spanUrl, label, start, end))
            }
        }
    } else {
        rawText = bodyText
    }

    // Regex for plain text URLs (http://, https://, or www.)
    val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"'\r\n]+""")
    val matches = urlRegex.findAll(rawText)

    for (match in matches) {
        val matchedStr = match.value
        val cleanedUrl = matchedStr.trimEnd('.', ',', ')', ']', '}', '>', ';', '!', '?', '"', '\'')
        if (cleanedUrl.length < 4) continue

        val matchStart = match.range.first
        val matchEnd = matchStart + cleanedUrl.length

        // Check for overlaps with already extracted HTML links
        val overlaps = links.any { existing ->
            (matchStart >= existing.startIndex && matchStart < existing.endIndex) ||
            (matchEnd > existing.startIndex && matchEnd <= existing.endIndex)
        }

        if (!overlaps) {
            links.add(
                ExtractedEmailLink(
                    url = cleanedUrl,
                    label = cleanedUrl,
                    startIndex = matchStart,
                    endIndex = matchEnd
                )
            )
        }
    }

    // Sort links by start index ascending
    links.sortBy { it.startIndex }

    val builder = AnnotatedString.Builder()
    var lastIdx = 0
    val detectedUrls = mutableListOf<String>()

    for (link in links) {
        if (link.startIndex > lastIdx && link.startIndex <= rawText.length) {
            builder.append(rawText.substring(lastIdx, link.startIndex))
        }

        if (link.startIndex < rawText.length && link.endIndex <= rawText.length && link.startIndex < link.endIndex) {
            val linkStart = builder.length
            val linkText = rawText.substring(link.startIndex, link.endIndex)
            builder.append(linkText)
            val linkEnd = builder.length

            // Format link text with bold cyan underline
            builder.addStyle(
                style = SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                ),
                start = linkStart,
                end = linkEnd
            )

            val fullUrl = when {
                link.url.startsWith("http://", ignoreCase = true) || link.url.startsWith("https://", ignoreCase = true) -> link.url
                else -> "https://${link.url}"
            }

            builder.addStringAnnotation(
                tag = "URL",
                annotation = fullUrl,
                start = linkStart,
                end = linkEnd
            )

            detectedUrls.add(fullUrl)
            lastIdx = link.endIndex
        }
    }

    if (lastIdx < rawText.length) {
        builder.append(rawText.substring(lastIdx))
    }

    return Pair(builder.toAnnotatedString(), detectedUrls.distinct())
}

@Composable
fun InteractiveEmailContent(
    bodyText: String,
    bodyHtml: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (annotatedString, _) = remember(bodyText, bodyHtml) {
        parseMessageContent(bodyText, bodyHtml)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Email Body Text with clickable inline links
        if (annotatedString.text.isNotBlank()) {
            ClickableText(
                text = annotatedString,
                style = TextStyle(
                    color = TextWhite,
                    fontSize = 15.sp,
                    lineHeight = 24.sp
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            openExternalUrl(context, annotation.item)
                        }
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "No content in email message.",
                fontSize = 14.sp,
                color = TextMuted
            )
        }
    }
}
