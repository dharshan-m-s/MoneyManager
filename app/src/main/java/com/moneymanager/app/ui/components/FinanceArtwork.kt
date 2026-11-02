package com.moneymanager.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Small animated, category-aware financial illustration. These are drawn locally with Compose
 * Canvas (no remote assets), so each screen keeps its own visual language without adding image
 * loading, memory pressure, or network dependencies.
 */
@Composable
fun FinanceArtwork(
    kind: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "financeArtwork")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "financeArtworkPhase"
    )
    val normalized = kind.lowercase()
    val secondary = when {
        normalized.contains("credit") || normalized.contains("debit") || normalized.contains("card") -> Color(0xFF8A9CFF)
        normalized.contains("bill_electricity") -> Color(0xFFFFC45B)
        normalized.contains("bill_water") -> Color(0xFF63C7F3)
        normalized.contains("bill_gas") -> Color(0xFFFF9A55)
        normalized.contains("bill_mobile") || normalized.contains("bill_broadband") -> Color(0xFF9D8BFF)
        normalized.contains("bill_rent") -> Color(0xFF6CCFA7)
        normalized.contains("bill_insurance") -> Color(0xFF6AB8D8)
        normalized.contains("bill_loan") -> Color(0xFFFFA66E)
        normalized.contains("income") || normalized.contains("cash") || normalized.contains("wallet") -> Color(0xFF75DDB4)
        normalized.contains("investment") -> Color(0xFF78C6E8)
        normalized.contains("loan") -> Color(0xFFFFB56B)
        else -> Color(0xFF9DABFF)
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val min = size.minDimension
            val cx = w * 0.5f
            val cy = h * 0.5f
            val pulse = 0.94f + phase * 0.08f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.20f), secondary.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = min * 0.54f
                ),
                radius = min * 0.49f,
                center = Offset(cx, cy)
            )
            when {
                normalized.startsWith("bill_electricity") -> {
                    drawRoundRect(secondary.copy(alpha = 0.18f), Offset(w*.24f,h*.12f), Size(w*.52f,h*.76f), androidx.compose.ui.geometry.CornerRadius(min*.12f))
                    val bolt = Path().apply {
                        moveTo(w*.56f,h*.22f); lineTo(w*.39f,h*.53f); lineTo(w*.51f,h*.53f)
                        lineTo(w*.43f,h*.78f); lineTo(w*.65f,h*.43f); lineTo(w*.53f,h*.43f); close()
                    }
                    drawPath(bolt, secondary, style = Stroke(width = min*.075f, cap = StrokeCap.Round))
                }
                normalized.startsWith("bill_water") -> {
                    val path = Path().apply {
                        moveTo(cx, h*.13f); cubicTo(w*.13f,h*.55f,w*.29f,h*.78f,cx,h*.82f)
                        cubicTo(w*.72f,h*.78f,w*.87f,h*.55f,cx,h*.13f)
                    }
                    drawPath(path, secondary.copy(alpha=.22f))
                    drawPath(path, secondary, style=Stroke(width=min*.065f, cap=StrokeCap.Round))
                    drawArc(secondary.copy(alpha=.7f),190f,110f,false,Offset(w*.34f,h*.48f),Size(w*.3f,h*.25f),style=Stroke(width=min*.04f,cap=StrokeCap.Round))
                }
                normalized.startsWith("bill_gas") -> {
                    drawCircle(secondary.copy(alpha=.18f),min*.36f,Offset(cx,cy))
                    drawOval(secondary,Offset(w*.37f,h*.22f),Size(w*.26f,h*.55f),style=Stroke(width=min*.065f,cap=StrokeCap.Round))
                    drawArc(secondary,210f,120f,false,Offset(w*.30f,h*.26f),Size(w*.4f,h*.5f),style=Stroke(width=min*.06f,cap=StrokeCap.Round))
                }
                normalized.startsWith("bill_mobile") || normalized.startsWith("bill_broadband") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.30f,h*.08f),Size(w*.40f,h*.84f),androidx.compose.ui.geometry.CornerRadius(min*.12f))
                    drawRoundRect(secondary,Offset(w*.34f,h*.13f),Size(w*.32f,h*.70f),androidx.compose.ui.geometry.CornerRadius(min*.09f),style=Stroke(width=min*.055f))
                    drawCircle(secondary,min*.025f,Offset(cx,h*.76f))
                    drawArc(secondary,205f,130f,false,Offset(w*.20f,h*.20f),Size(w*.60f,h*.48f),style=Stroke(width=min*.04f,cap=StrokeCap.Round))
                }
                normalized.startsWith("bill_education") || normalized.startsWith("bill_school") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.20f,h*.18f),Size(w*.60f,h*.64f),androidx.compose.ui.geometry.CornerRadius(min*.07f))
                    drawPath(Path().apply { moveTo(w*.28f,h*.35f); lineTo(cx,h*.22f); lineTo(w*.72f,h*.35f); lineTo(cx,h*.49f); close() },secondary,style=Stroke(width=min*.055f,cap=StrokeCap.Round))
                    drawLine(secondary,Offset(w*.34f,h*.54f),Offset(w*.66f,h*.54f),min*.045f,cap=StrokeCap.Round)
                    drawLine(secondary,Offset(w*.38f,h*.65f),Offset(w*.62f,h*.65f),min*.045f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_subscription") || normalized.startsWith("bill_streaming") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.17f,h*.18f),Size(w*.66f,h*.64f),androidx.compose.ui.geometry.CornerRadius(min*.12f))
                    drawCircle(secondary, min*.20f, Offset(cx,cy))
                    drawPath(Path().apply { moveTo(w*.46f,h*.38f); lineTo(w*.46f,h*.62f); lineTo(w*.65f,cy); close() },Color.White.copy(alpha=.92f))
                }
                normalized.startsWith("bill_vehicle") || normalized.startsWith("bill_fuel") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.20f,h*.35f),Size(w*.60f,h*.36f),androidx.compose.ui.geometry.CornerRadius(min*.10f))
                    drawRoundRect(secondary,Offset(w*.29f,h*.25f),Size(w*.42f,h*.22f),androidx.compose.ui.geometry.CornerRadius(min*.08f),style=Stroke(width=min*.05f))
                    drawCircle(secondary,min*.075f,Offset(w*.34f,h*.72f))
                    drawCircle(secondary,min*.075f,Offset(w*.67f,h*.72f))
                }
                normalized.startsWith("bill_property") || normalized.startsWith("bill_tax") -> {
                    val roof=Path().apply { moveTo(w*.17f,h*.42f); lineTo(cx,h*.16f); lineTo(w*.83f,h*.42f) }
                    drawPath(roof,secondary,style=Stroke(width=min*.07f,cap=StrokeCap.Round))
                    drawRoundRect(secondary.copy(alpha=.2f),Offset(w*.25f,h*.40f),Size(w*.50f,h*.43f),androidx.compose.ui.geometry.CornerRadius(min*.035f))
                    for (x in listOf(.37f,.51f,.65f)) drawLine(secondary,Offset(w*x,h*.49f),Offset(w*x,h*.76f),min*.045f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_rent") -> {
                    val roof=Path().apply { moveTo(w*.19f,h*.43f); lineTo(cx,h*.16f); lineTo(w*.81f,h*.43f) }
                    drawPath(roof,secondary,style=Stroke(width=min*.07f,cap=StrokeCap.Round))
                    drawRoundRect(secondary.copy(alpha=.2f),Offset(w*.27f,h*.42f),Size(w*.46f,h*.42f),androidx.compose.ui.geometry.CornerRadius(min*.04f))
                    drawRoundRect(secondary,Offset(w*.43f,h*.57f),Size(w*.15f,h*.27f),androidx.compose.ui.geometry.CornerRadius(min*.025f),style=Stroke(width=min*.045f))
                }
                normalized.startsWith("bill_insurance") -> {
                    val shield=Path().apply { moveTo(cx,h*.12f); lineTo(w*.78f,h*.25f); lineTo(w*.73f,h*.59f); quadraticTo(cx,h*.88f,w*.27f,h*.59f); lineTo(w*.22f,h*.25f); close() }
                    drawPath(shield,secondary.copy(alpha=.2f))
                    drawPath(shield,secondary,style=Stroke(width=min*.055f,cap=StrokeCap.Round))
                    drawLine(secondary,Offset(w*.38f,h*.48f),Offset(w*.48f,h*.59f),min*.055f,cap=StrokeCap.Round)
                    drawLine(secondary,Offset(w*.48f,h*.59f),Offset(w*.66f,h*.38f),min*.055f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_dth") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.13f,h*.23f),Size(w*.74f,h*.48f),androidx.compose.ui.geometry.CornerRadius(min*.08f))
                    drawRoundRect(secondary,Offset(w*.20f,h*.29f),Size(w*.60f,h*.34f),androidx.compose.ui.geometry.CornerRadius(min*.045f),style=Stroke(width=min*.055f))
                    drawLine(secondary,Offset(cx,h*.71f),Offset(cx,h*.80f),min*.05f,cap=StrokeCap.Round)
                    drawLine(secondary,Offset(w*.37f,h*.81f),Offset(w*.63f,h*.81f),min*.05f,cap=StrokeCap.Round)
                    drawArc(secondary,205f,130f,false,Offset(w*.32f,h*.06f),Size(w*.36f,h*.24f),style=Stroke(width=min*.04f,cap=StrokeCap.Round))
                }
                normalized.startsWith("bill_pension") -> {
                    drawCircle(secondary.copy(alpha=.18f),min*.36f,Offset(cx,cy))
                    drawArc(secondary,-90f,270f*pulse,false,Offset(w*.18f,h*.18f),Size(w*.64f,h*.64f),style=Stroke(width=min*.07f,cap=StrokeCap.Round))
                    drawCircle(secondary,min*.12f,Offset(cx,cy))
                    drawLine(Color.White.copy(alpha=.85f),Offset(cx,h*.34f),Offset(cx,h*.66f),min*.045f,cap=StrokeCap.Round)
                    drawLine(Color.White.copy(alpha=.85f),Offset(w*.39f,cy),Offset(w*.61f,cy),min*.045f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_other") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.23f,h*.12f),Size(w*.54f,h*.76f),androidx.compose.ui.geometry.CornerRadius(min*.07f))
                    drawRoundRect(secondary,Offset(w*.29f,h*.18f),Size(w*.42f,h*.64f),androidx.compose.ui.geometry.CornerRadius(min*.045f),style=Stroke(width=min*.05f))
                    for (y in listOf(.38f,.50f,.62f)) drawLine(secondary,Offset(w*.37f,h*y),Offset(w*.63f,h*y),min*.04f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_dth") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.13f,h*.23f),Size(w*.74f,h*.48f),androidx.compose.ui.geometry.CornerRadius(min*.08f))
                    drawRoundRect(secondary,Offset(w*.20f,h*.29f),Size(w*.60f,h*.34f),androidx.compose.ui.geometry.CornerRadius(min*.045f),style=Stroke(width=min*.055f))
                    drawLine(secondary,Offset(cx,h*.71f),Offset(cx,h*.80f),min*.05f,cap=StrokeCap.Round)
                    drawLine(secondary,Offset(w*.37f,h*.81f),Offset(w*.63f,h*.81f),min*.05f,cap=StrokeCap.Round)
                    drawArc(secondary,205f,130f,false,Offset(w*.32f,h*.06f),Size(w*.36f,h*.24f),style=Stroke(width=min*.04f,cap=StrokeCap.Round))
                }
                normalized.startsWith("bill_loan") || normalized.startsWith("bill_credit_card") -> {
                    drawRoundRect(secondary.copy(alpha=.2f),Offset(w*.15f,h*.24f),Size(w*.70f,h*.52f),androidx.compose.ui.geometry.CornerRadius(min*.10f))
                    drawRoundRect(secondary,Offset(w*.21f,h*.31f),Size(w*.58f,h*.37f),androidx.compose.ui.geometry.CornerRadius(min*.07f),style=Stroke(width=min*.045f))
                    drawRect(secondary,Offset(w*.22f,h*.43f),Size(w*.56f,h*.07f))
                    drawCircle(secondary,min*.045f,Offset(w*.68f,h*.59f))
                }
                normalized.contains("credit") || normalized.contains("debit") || normalized.contains("prepaid") || normalized.contains("card") -> {
                    val tilt = (phase-.5f)*min*.025f
                    drawRoundRect(secondary.copy(alpha=.16f),Offset(w*.12f,h*.22f+tilt),Size(w*.76f,h*.54f),androidx.compose.ui.geometry.CornerRadius(min*.12f))
                    drawRoundRect(Brush.linearGradient(listOf(tint.copy(alpha=.95f),secondary.copy(alpha=.72f))),Offset(w*.17f,h*.16f-tilt),Size(w*.70f,h*.54f),androidx.compose.ui.geometry.CornerRadius(min*.11f))
                    drawRect(Color.White.copy(alpha=.62f),Offset(w*.19f,h*.31f-tilt),Size(w*.66f,h*.075f))
                    drawRoundRect(Color.White.copy(alpha=.8f),Offset(w*.22f,h*.53f-tilt),Size(w*.17f,h*.08f),androidx.compose.ui.geometry.CornerRadius(min*.025f))
                    drawCircle(Color.White.copy(alpha=.75f),min*.045f,Offset(w*.75f,h*.60f-tilt))
                }
                normalized.contains("bank") -> {
                    drawRoundRect(secondary.copy(alpha=.16f),Offset(w*.16f,h*.22f),Size(w*.68f,h*.58f),androidx.compose.ui.geometry.CornerRadius(min*.10f))
                    val roof=Path().apply { moveTo(w*.18f,h*.34f); lineTo(cx,h*.12f); lineTo(w*.82f,h*.34f); close() }
                    drawPath(roof,secondary)
                    for (x in listOf(.31f,.46f,.61f)) drawRoundRect(secondary,Offset(w*x,h*.38f),Size(w*.075f,h*.27f),androidx.compose.ui.geometry.CornerRadius(min*.025f))
                    drawLine(secondary,Offset(w*.23f,h*.70f),Offset(w*.77f,h*.70f),min*.055f,cap=StrokeCap.Round)
                }
                normalized.contains("income") -> {
                    drawArc(secondary.copy(alpha=.35f),-210f,240f,false,Offset(w*.16f,h*.12f),Size(w*.68f,h*.68f),style=Stroke(width=min*.10f,cap=StrokeCap.Round))
                    val trend=Path().apply { moveTo(w*.22f,h*.65f); lineTo(w*.43f,h*.47f); lineTo(w*.57f,h*.55f); lineTo(w*.78f,h*.28f) }
                    drawPath(trend,secondary,style=Stroke(width=min*.075f,cap=StrokeCap.Round))
                    drawCircle(secondary,min*.055f,Offset(w*.78f,h*.28f))
                }
                normalized.contains("cash") || normalized.contains("wallet") -> {
                    drawRoundRect(secondary.copy(alpha=.2f),Offset(w*.15f,h*.27f),Size(w*.70f,h*.48f),androidx.compose.ui.geometry.CornerRadius(min*.10f))
                    drawRoundRect(secondary,Offset(w*.22f,h*.20f),Size(w*.56f,h*.48f),androidx.compose.ui.geometry.CornerRadius(min*.09f),style=Stroke(width=min*.06f))
                    drawRoundRect(secondary,Offset(w*.58f,h*.38f),Size(w*.23f,h*.17f),androidx.compose.ui.geometry.CornerRadius(min*.045f))
                    drawCircle(secondary,min*.035f,Offset(w*.67f,h*.465f))
                }
                normalized.contains("investment") -> {
                    drawLine(secondary.copy(alpha=.55f),Offset(w*.18f,h*.77f),Offset(w*.82f,h*.77f),min*.025f)
                    val heights=listOf(.30f,.48f,.39f,.68f,.83f)
                    heights.forEachIndexed { i, v ->
                        val bw=w*.095f
                        drawRoundRect(secondary.copy(alpha=.55f+phase*.3f),Offset(w*(.20f+i*.13f),h*(.77f-v*.65f)),Size(bw,h*v*.65f),androidx.compose.ui.geometry.CornerRadius(min*.025f))
                    }
                    drawPath(Path().apply { moveTo(w*.18f,h*.43f); lineTo(w*.40f,h*.32f); lineTo(w*.56f,h*.36f); lineTo(w*.79f,h*.16f) },secondary,style=Stroke(width=min*.045f,cap=StrokeCap.Round))
                }
                normalized.contains("loan") -> {
                    drawCircle(secondary.copy(alpha=.18f),min*.38f,Offset(cx,cy))
                    drawArc(secondary,-90f,270f*pulse,false,Offset(w*.18f,h*.18f),Size(w*.64f,h*.64f),style=Stroke(width=min*.085f,cap=StrokeCap.Round))
                    drawLine(secondary,Offset(w*.34f,h*.54f),Offset(w*.48f,h*.67f),min*.06f,cap=StrokeCap.Round)
                    drawLine(secondary,Offset(w*.48f,h*.67f),Offset(w*.69f,h*.37f),min*.06f,cap=StrokeCap.Round)
                }
                normalized.startsWith("bill_other") -> {
                    drawRoundRect(secondary.copy(alpha=.18f),Offset(w*.23f,h*.12f),Size(w*.54f,h*.76f),androidx.compose.ui.geometry.CornerRadius(min*.07f))
                    drawRoundRect(secondary,Offset(w*.29f,h*.18f),Size(w*.42f,h*.64f),androidx.compose.ui.geometry.CornerRadius(min*.045f),style=Stroke(width=min*.05f))
                    for (y in listOf(.38f,.50f,.62f)) drawLine(secondary,Offset(w*.37f,h*y),Offset(w*.63f,h*y),min*.04f,cap=StrokeCap.Round)
                }
                else -> {
                    drawCircle(secondary.copy(alpha=.22f),min*.35f,Offset(cx,cy))
                    drawArc(secondary,-90f,260f*pulse,false,Offset(w*.18f,h*.18f),Size(w*.64f,h*.64f),style=Stroke(width=min*.07f,cap=StrokeCap.Round))
                    drawCircle(tint,min*.08f,Offset(cx,cy))
                }
            }
            drawCircle(Color.White.copy(alpha=.5f),min*.018f,Offset(cx+sin(phase*PI.toFloat()*2f)*min*.34f,cy-min*.35f))
        }
    }
}
