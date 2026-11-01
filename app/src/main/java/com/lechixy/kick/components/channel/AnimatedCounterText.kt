package com.lechixy.kick.components.channel

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.util.FormatUtils

/** Hızlı başlayıp yumuşakça yerine oturan (easeOutQuint benzeri) odometer eğrisi. */
private val OdometerEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private const val ANIMATION_DURATION_MS = 2400
private const val DIGIT_STAGGER_MS = 90L
private const val EXTRA_SPINS = 1

private class CountTracker {
    var last: Int = 0
    var spinGeneration: Int = 0
    var hasRendered: Boolean = false
}

/**
 * Odometer / sayaç tarzı sayı gösterimi.
 * Her rakam 0-9 arası dikey bir "tekerlek"tir; değer değiştiğinde tekerlek
 * fade olmadan yukarı (artış) veya aşağı (azalış) kayarak yeni rakama döner.
 */
@Composable
fun AnimatedCounterText(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.primary,
    formatViewers: Boolean = true
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatted = remember(count, formatViewers, locale) {
        if (formatViewers) FormatUtils.formatViewersCount(count, locale) else java.text.NumberFormat.getIntegerInstance(locale).format(count)
    }

    val tracker = remember { CountTracker() }
    val initialEntrance = remember(count) { !tracker.hasRendered }
    val isIncreasing = remember(count) {
        if (!tracker.hasRendered) true else count >= tracker.last
    }
    val spinGeneration = remember(count) {
        if (!tracker.hasRendered || count != tracker.last) {
            tracker.spinGeneration++
        }
        tracker.spinGeneration
    }
    SideEffect {
        tracker.last = count
        tracker.hasRendered = true
    }

    // "tnum": tüm rakamlar eşit genişlikte olsun ki tekerlekler dönerken yazı zıplamasın
    val textStyle = remember(style, fontWeight, color) {
        style.merge(
            TextStyle(
                color = color,
                fontWeight = fontWeight,
                fontFeatureSettings = "tnum"
            )
        )
    }

    Row(
        modifier = modifier
            .animateContentSize(animationSpec = tween(if (initialEntrance) ANIMATION_DURATION_MS / 2 else 400, easing = OdometerEasing))
            .clearAndSetSemantics { contentDescription = formatted },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val length = formatted.length
        formatted.forEachIndexed { index, char ->
            // Sağdan basamak indeksiyle key'le: sayı uzayıp kısalsa da birler/onlar basamağı aynı tekerlekte kalır
            val placeFromRight = length - 1 - index
            key(placeFromRight) {
                if (char.isDigit()) {
                    OdometerDigit(
                        digit = char.digitToInt(),
                        zeroDigit = (char.code - char.digitToInt()).toChar(),
                        spinGeneration = spinGeneration,
                        isIncreasing = isIncreasing,
                        initialEntrance = initialEntrance,
                        startDelayMs = if (initialEntrance) index * DIGIT_STAGGER_MS else 0L,
                        style = textStyle
                    )
                } else {
                    Text(text = char.toString(), style = textStyle)
                }
            }
        }
    }
}

@Composable
private fun OdometerDigit(
    digit: Int,
    zeroDigit: Char,
    spinGeneration: Int,
    isIncreasing: Boolean,
    initialEntrance: Boolean,
    startDelayMs: Long,
    style: TextStyle
) {
    // Sürekli (sınırsız) tekerlek pozisyonu: 12.4f → "2" ile "3" arasında, %40 kaymış
    val position = remember { Animatable(0f) }

    LaunchedEffect(digit, spinGeneration) {
        val from = position.value
        val fromDigit = from.roundToInt().mod(10)
        var steps = digit - fromDigit
        if (isIncreasing) {
            if (steps < 0) steps += 10      // artışta hep yukarı dön (7 → 8 → 9 → 0 → 1 → 2)
            if (initialEntrance) steps += 10 * EXTRA_SPINS
        } else {
            if (steps > 0) steps -= 10      // azalışta hep aşağı dön
            if (initialEntrance) steps -= 10 * EXTRA_SPINS
        }

        if (startDelayMs > 0) delay(startDelayMs.milliseconds)
        position.animateTo(
            targetValue = from.roundToInt().toFloat() + steps,
            animationSpec = tween(
                durationMillis = if (initialEntrance) ANIMATION_DURATION_MS else 400,
                easing = OdometerEasing
            )
        )
    }

    // Sadece tam rakam değiştiğinde recompose; ara kayma graphicsLayer'da (draw fazında) okunuyor
    val base by remember { derivedStateOf { floor(position.value).toInt() } }

    Box(Modifier.clipToBounds()) {
        Text(
            text = (zeroDigit.code + base.mod(10)).toChar().toString(),
            style = style,
            modifier = Modifier.graphicsLayer {
                val p = position.value
                translationY = -(p - floor(p)) * size.height
            }
        )
        Text(
            text = (zeroDigit.code + (base + 1).mod(10)).toChar().toString(),
            style = style,
            modifier = Modifier.graphicsLayer {
                val p = position.value
                translationY = (1f - (p - floor(p))) * size.height
            }
        )
    }
}
