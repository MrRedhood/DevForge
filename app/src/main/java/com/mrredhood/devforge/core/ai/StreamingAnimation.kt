package com.mrredhood.devforge.core.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random

enum class StreamingAnimationKind(
    val title: String,
    val glyph: String,
) {
    HAMMER("Hammer", "🔨"),
    CHAINSAW("Chainsaw", "🪚"),
    RUNNING_MAN("Running man", "🏃‍♂️"),
    THINKING_MAN("Thinking man", "🤔"),
    TYPING("Typing", "⌨️"),
    ROCKET("Rocket", "🚀"),
    COFFEE("Coffee break", "☕"),
    GEAR("Gear", "⚙️"),
    SPARK("Spark", "✨"),
    WAVE("Wave", "👋"),
    FIRE("Fire", "🔥"),
    ROBOT("Robot", "🤖");

    companion object {
        fun fromIndex(index: Int): StreamingAnimationKind {
            val values = entries
            return values[Math.floorMod(index, values.size)]
        }

        fun random(): StreamingAnimationKind = entries[Random.nextInt(entries.size)]
    }
}

@Composable
fun StreamingAnimation(
    kind: StreamingAnimationKind = remember {
        StreamingAnimationKind.fromIndex(Random.nextInt(StreamingAnimationKind.entries.size))
    },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val transition = rememberInfiniteTransition(label = "streaming-animation")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    val motion = remember(kind, phase, spin) {
        when (kind) {
            StreamingAnimationKind.HAMMER -> Motion(rotation = -35f + (70f * phase))
            StreamingAnimationKind.CHAINSAW -> Motion(
                rotation = -3f + (6f * phase),
                x = -4f + (8f * phase),
                scale = .98f + (.04f * phase),
            )
            StreamingAnimationKind.RUNNING_MAN -> Motion(
                x = -5f + (10f * phase),
                y = 5f - (10f * phase),
                rotation = -4f + (8f * phase),
            )
            StreamingAnimationKind.THINKING_MAN -> Motion(
                y = 1.5f - (3f * phase),
                scale = .98f + (.04f * phase),
            )
            StreamingAnimationKind.TYPING -> Motion(
                x = -2f + (4f * phase),
                y = 2f - (4f * phase),
                rotation = -5f + (10f * phase),
            )
            StreamingAnimationKind.ROCKET -> Motion(
                y = 8f - (28f * phase),
                x = -2f + (4f * phase),
                rotation = -5f + (10f * phase),
            )
            StreamingAnimationKind.COFFEE -> Motion(
                rotation = -10f + (20f * phase),
                y = 1f - (2f * phase),
            )
            StreamingAnimationKind.GEAR -> Motion(rotation = spin)
            StreamingAnimationKind.SPARK -> Motion(
                rotation = -20f + (40f * phase),
                scale = .82f + (.36f * phase),
            )
            StreamingAnimationKind.WAVE -> Motion(
                rotation = -25f + (50f * phase),
                y = 1f - (2f * phase),
            )
            StreamingAnimationKind.FIRE -> Motion(
                x = -1.5f + (3f * phase),
                rotation = -3f + (6f * phase),
                scale = .96f + (.12f * phase),
            )
            StreamingAnimationKind.ROBOT -> Motion(
                y = 2f - (4f * phase),
                rotation = -2f + (4f * phase),
                scale = .98f + (.04f * phase),
            )
        }
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .semantics { contentDescription = "${kind.title} streaming animation" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.glyph,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontSize = 25.sp,
            modifier = Modifier.graphicsLayer {
                translationX = motion.x * density
                translationY = motion.y * density
                rotationZ = motion.rotation
                scaleX = motion.scale
                scaleY = motion.scale
            },
        )
    }
}

private data class Motion(
    val rotation: Float = 0f,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
)
