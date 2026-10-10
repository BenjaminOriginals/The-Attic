package com.focuslockout.overlay.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslockout.R
import com.focuslockout.overlay.MathFeedback
import com.focuslockout.overlay.MathState
import com.focuslockout.ui.components.FocusCard
import com.focuslockout.ui.components.PrimaryActionButton
import com.focuslockout.ui.theme.neonGlow

/** Math wall: solve [target] multi-step problems in a row; one wrong answer resets the streak. */
@Composable
fun MathChallenge(
    state: MathState,
    target: Int,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val problem = state.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.math_instructions, target),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(target) { i ->
                val solved = i < state.index
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (solved) colors.primary else colors.surfaceVariant)
                        .border(1.dp, if (solved) colors.primary else colors.outline, CircleShape),
                )
            }
        }

        if (problem != null) {
            FocusCard {
                Text(
                    text = stringResource(R.string.math_problem_counter, state.index + 1, target),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                Text(
                    text = stringResource(R.string.math_problem_format, problem.displayExpression),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displaySmall.neonGlow(),
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                ),
                placeholder = { Text(stringResource(R.string.math_answer_hint)) },
                isError = state.feedback == MathFeedback.WRONG,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outline,
                    cursorColor = colors.primary,
                    errorBorderColor = colors.error,
                ),
            )

            when (state.feedback) {
                MathFeedback.WRONG -> Text(
                    text = stringResource(R.string.math_wrong, target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
                MathFeedback.CORRECT -> Text(
                    text = stringResource(R.string.math_correct),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                )
                MathFeedback.NONE -> Unit
            }

            PrimaryActionButton(
                text = stringResource(R.string.math_submit),
                onClick = onSubmit,
                enabled = state.input.isNotEmpty(),
            )
        }
    }
}
