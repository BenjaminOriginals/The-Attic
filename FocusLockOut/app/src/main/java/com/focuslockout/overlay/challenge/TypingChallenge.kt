package com.focuslockout.overlay.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslockout.R
import com.focuslockout.overlay.TypingState
import com.focuslockout.ui.components.FocusCard

/**
 * Typing penalty: reproduce an absurd passage exactly. The passage shows typed-correct text in
 * green, the first wrong character in red, and the input underlines everything after a mistake.
 */
@Composable
fun TypingChallenge(state: TypingState, onInputChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val mismatch = state.mismatchIndex
    val monoStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, lineHeight = 26.sp)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.typing_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        FocusCard(borderColor = if (mismatch >= 0) colors.error else colors.outline) {
            Text(
                text = highlightedPassage(
                    passage = state.passage,
                    correctChars = state.correctChars,
                    mismatch = mismatch,
                    typedColor = colors.primary,
                    pendingColor = colors.onSurfaceVariant,
                    errorColor = colors.error,
                    cursorColor = colors.onSurface,
                ),
                style = monoStyle,
            )
        }

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (mismatch >= 0) colors.error else colors.primary,
            trackColor = colors.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            textStyle = monoStyle,
            placeholder = { Text(stringResource(R.string.typing_placeholder)) },
            isError = mismatch >= 0,
            visualTransformation = ErrorTailTransformation(mismatch, colors.error),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
            ),
            supportingText = {
                val text = when {
                    state.pasteBlocked -> stringResource(R.string.typing_paste_blocked)
                    mismatch >= 0 -> stringResource(R.string.typing_mismatch, mismatch + 1)
                    else -> stringResource(R.string.typing_progress, state.correctChars, state.passage.length)
                }
                Text(text)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                cursorColor = colors.primary,
                errorBorderColor = colors.error,
                errorCursorColor = colors.error,
                errorSupportingTextColor = colors.error,
            ),
        )
    }
}

private fun highlightedPassage(
    passage: String,
    correctChars: Int,
    mismatch: Int,
    typedColor: Color,
    pendingColor: Color,
    errorColor: Color,
    cursorColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(passage)
    addStyle(SpanStyle(color = pendingColor), 0, passage.length)
    val typedEnd = correctChars.coerceAtMost(passage.length)
    if (typedEnd > 0) addStyle(SpanStyle(color = typedColor), 0, typedEnd)
    if (mismatch in passage.indices) {
        addStyle(
            SpanStyle(color = Color.Black, background = errorColor, fontWeight = FontWeight.Bold),
            mismatch,
            mismatch + 1,
        )
    } else if (mismatch < 0 && typedEnd < passage.length) {
        // Next character to type.
        addStyle(SpanStyle(color = cursorColor, textDecoration = TextDecoration.Underline), typedEnd, typedEnd + 1)
    }
}

/** Colors everything from the first wrong character onwards; text and offsets are untouched. */
private data class ErrorTailTransformation(
    private val errorStart: Int,
    private val errorColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (errorStart < 0 || errorStart >= text.length) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(color = errorColor, textDecoration = TextDecoration.LineThrough),
                errorStart,
                text.length,
            )
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}
