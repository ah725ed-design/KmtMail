package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * Ensures the generated temporary email address is prominent, vertically centered,
 * occupies almost the entire width naturally, and scales dynamically down if needed.
 */
@Composable
fun NonWrappingEmailText(
    email: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val fontSize = when {
        email.length <= 22 -> 24.sp
        email.length <= 26 -> 21.sp
        email.length <= 30 -> 18.sp
        email.length <= 35 -> 15.sp
        else -> 13.sp
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SelectionContainer {
            Text(
                text = email,
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Default,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
