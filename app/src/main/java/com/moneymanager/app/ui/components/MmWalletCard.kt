package com.moneymanager.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.data.local.entity.AccountEntity
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.Money
import kotlinx.coroutines.delay

/**
 * A tactile, issuer-coloured wallet card. The art is decorative only: every piece of account
 * identity shown here comes from the account model, and only the last four digits are rendered.
 */
@Composable
fun MmWalletCard(
    issuer: String,
    title: String,
    typeLabel: String,
    maskedNumber: String? = null,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val issuerName = issuer.ifBlank { "Bank" }
    val digits = maskedNumber.orEmpty().filter(Char::isDigit).takeLast(4)
    val numberText = if (digits.length == 4) {
        "••••  ••••  ••••  $digits"
    } else {
        "CARD DETAILS NOT ADDED"
    }
    val palette = walletPalette(issuerName)
    val shape = RoundedCornerShape(27.dp)

    // A single entrance glint, not a perpetual animation. The card stays still after it settles.
    val sheen = remember { Animatable(-0.55f) }
    LaunchedEffect(Unit) {
        delay(140)
        sheen.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 1_100, easing = FastOutSlowInEasing)
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tilt by animateFloatAsState(
        targetValue = if (pressed) -0.85f else 0f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 760f),
        label = "walletCardTilt"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.018f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 720f),
        label = "walletCardLift"
    )
    val interaction = if (onClick != null) {
        Modifier
            .graphicsLayer {
                rotationZ = tilt
                scaleX = lift
                scaleY = lift
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        Modifier
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(palette))
            .then(interaction)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.52f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.30f))
                ),
                shape = shape
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width * 0.88f, size.height * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.025f), Color.Transparent),
                    center = glowCenter,
                    radius = size.minDimension * 0.84f
                ),
                radius = size.minDimension * 0.84f,
                center = glowCenter
            )

            // Layered orbital lines provide a restrained holographic impression without
            // pretending to be a real payment-network or contactless mark.
            val orbitCenter = Offset(size.width * 0.91f, size.height * 0.55f)
            for (ring in 0..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.035f + ring * 0.009f),
                    radius = size.minDimension * (0.19f + ring * 0.075f),
                    center = orbitCenter,
                    style = Stroke(width = (0.8f + ring * 0.12f).dp.toPx())
                )
            }
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 204f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = Offset(size.width * 0.57f, size.height * 0.08f),
                size = Size(size.width * 0.72f, size.height * 1.06f),
                style = Stroke(width = 1.05.dp.toPx())
            )
            drawArc(
                color = Color.Black.copy(alpha = 0.12f),
                startAngle = 24f,
                sweepAngle = 126f,
                useCenter = false,
                topLeft = Offset(size.width * 0.68f, size.height * 0.20f),
                size = Size(size.width * 0.56f, size.height * 0.84f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            val flowingLine = Path().apply {
                moveTo(size.width * 0.34f, size.height * 1.03f)
                cubicTo(
                    size.width * 0.56f, size.height * 0.72f,
                    size.width * 0.58f, size.height * 0.33f,
                    size.width * 1.04f, size.height * 0.06f
                )
            }
            drawPath(
                path = flowingLine,
                color = Color.White.copy(alpha = 0.075f),
                style = Stroke(width = 1.1.dp.toPx())
            )

            // One soft highlight sweeps over the surface when it first enters the screen.
            if (sheen.value < 1.15f) {
                val sweepX = size.width * sheen.value
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.015f),
                            Color.White.copy(alpha = 0.115f),
                            Color.White.copy(alpha = 0.015f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - size.width * 0.28f, 0f),
                        end = Offset(sweepX + size.width * 0.18f, size.height)
                    )
                )
            }

            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(
                    (size.width - 2.dp.toPx()).coerceAtLeast(0f),
                    (size.height - 2.dp.toPx()).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(26.dp.toPx()),
                style = Stroke(width = 0.7.dp.toPx())
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 19.dp, vertical = 17.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.97f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(11.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    MmBankLogo(issuerName, Modifier.width(96.dp).height(31.dp))
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (active) "ACCOUNT ACTIVE" else "ACCOUNT INACTIVE",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.0.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.13f))
                            .border(0.7.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            typeLabel.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletEmvChip()
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        if (digits.length == 4) numberText else "CARD DETAILS NOT ADDED",
                        color = Color.White.copy(alpha = if (digits.length == 4) 0.96f else 0.68f),
                        fontSize = if (digits.length == 4) 11.sp else 8.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = if (digits.length == 4) 0.75.sp else 1.0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (digits.length == 4) {
                        Text(
                            "SECURELY MASKED",
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 6.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ACCOUNT LABEL",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 7.sp,
                        letterSpacing = 1.25.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        title.ifBlank { issuerName },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.width(5.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.78f)))
                    Box(Modifier.width(5.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.46f)))
                    Box(Modifier.width(5.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f)))
                }
            }
        }
    }
}

@Composable
fun MmWalletCard(
    account: AccountEntity,
    modifier: Modifier = Modifier,
    maskedNumberOverride: String? = null,
    onClick: (() -> Unit)? = null
) {
    val suffix = if (account.accountType == AccountType.CREDIT_CARD || account.accountType == AccountType.DEBIT_CARD) {
        maskedCardNumberFromSourceId(account.sourceAccountId)
    } else null
    val overrideSuffix = maskedCardNumberFromSourceId(maskedNumberOverride)
    val typeLabel = when (account.accountType) {
        AccountType.CREDIT_CARD -> "Credit"
        AccountType.DEBIT_CARD -> "Debit"
        AccountType.PREPAID_CARD -> "Prepaid"
        else -> "Card"
    }
    MmWalletCard(
        issuer = account.institutionName.ifBlank { account.nickname },
        title = account.nickname.ifBlank { account.institutionName },
        typeLabel = typeLabel,
        maskedNumber = overrideSuffix ?: suffix,
        active = account.active,
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * A rich bank account card that mirrors the wallet-card language without pretending to be a
 * payment card. Only a masked account suffix is shown; the art remains decorative.
 */
@Composable
fun MmBankAccountCard(
    account: AccountEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val issuer = account.institutionName.ifBlank { "Bank" }
    val palette = bankAccountPalette(issuer)
    val shape = RoundedCornerShape(26.dp)
    val suffix = account.sourceAccountId.filter(Char::isDigit).takeLast(4)
    val numberText = if (suffix.length == 4) "••••  $suffix" else "ACCOUNT DETAILS PRIVATE"
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.986f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 720f),
        label = "bankAccountCardScale"
    )
    val sheen = remember(account.id) { Animatable(-0.48f) }
    LaunchedEffect(account.id) {
        delay(120)
        sheen.animateTo(1.15f, animationSpec = tween(980, easing = FastOutSlowInEasing))
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.82f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(palette))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.44f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.25f))
                ),
                shape
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.92f, size.height * 0.57f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.02f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.86f
                ),
                radius = size.minDimension * 0.86f,
                center = center
            )
            for (ring in 0..4) {
                drawArc(
                    color = Color.White.copy(alpha = 0.04f + ring * 0.014f),
                    startAngle = 192f + ring * 2f,
                    sweepAngle = 145f,
                    useCenter = false,
                    topLeft = Offset(size.width * (0.60f - ring * 0.022f), size.height * (0.02f - ring * 0.02f)),
                    size = Size(size.width * (0.72f + ring * 0.055f), size.height * (0.96f + ring * 0.08f)),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            if (sheen.value < 1.12f) {
                val x = size.width * sheen.value
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.115f), Color.Transparent),
                        start = Offset(x - size.width * 0.24f, 0f),
                        end = Offset(x + size.width * 0.16f, size.height)
                    )
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.98f))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    MmBankLogo(issuer, Modifier.width(88.dp).height(30.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (account.active) "BANK ACCOUNT" else "INACTIVE ACCOUNT",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "AVAILABLE BALANCE",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.15.sp
            )
            Spacer(Modifier.height(4.dp))
            MmAnimatedAmount(
                value = account.currentBalanceMinorUnits,
                formatter = { MoneyFormat.rupeesNoDecimals(Money(it)) },
                modifier = Modifier.fillMaxWidth(),
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 25.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        account.nickname.ifBlank { issuer },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        numberText,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 8.sp,
                        letterSpacing = 1.1.sp
                    )
                }
                Text(
                    if (account.active) "ACTIVE" else "PAUSED",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }
        }
    }
}

private fun bankAccountPalette(issuer: String): List<Color> = when {
    issuer.contains("FEDERAL", ignoreCase = true) ->
        listOf(Color(0xFF004CBE), Color(0xFF1C70D9), Color(0xFF00265F))
    issuer.contains("HDFC", ignoreCase = true) ->
        listOf(Color(0xFF0B477E), Color(0xFF2376B5), Color(0xFF082C4D))
    issuer.contains("ICICI", ignoreCase = true) ->
        listOf(Color(0xFF3A2A65), Color(0xFF74519A), Color(0xFF241B49))
    issuer.contains("SBI", ignoreCase = true) || issuer.contains("STATE BANK", ignoreCase = true) ->
        listOf(Color(0xFF075B8F), Color(0xFF2488B8), Color(0xFF05385B))
    issuer.contains("AXIS", ignoreCase = true) ->
        listOf(Color(0xFF64304D), Color(0xFF9B5570), Color(0xFF3C2038))
    issuer.contains("PUNJAB", ignoreCase = true) || issuer.contains("PNB", ignoreCase = true) ->
        listOf(Color(0xFF7B2447), Color(0xFFB34A6A), Color(0xFF44182F))
    issuer.contains("KOTAK", ignoreCase = true) ->
        listOf(Color(0xFF5D1D2D), Color(0xFF9B3C51), Color(0xFF351724))
    issuer.contains("YES", ignoreCase = true) ->
        listOf(Color(0xFF145D56), Color(0xFF2B9A8A), Color(0xFF0D3938))
    issuer.contains("UNION", ignoreCase = true) ->
        listOf(Color(0xFF14506D), Color(0xFF2F8B9B), Color(0xFF102F48))
    issuer.contains("CANARA", ignoreCase = true) ->
        listOf(Color(0xFF164B8C), Color(0xFF3386C5), Color(0xFF0D2F5E))
    issuer.contains("INDUSIND", ignoreCase = true) ->
        listOf(Color(0xFF174E64), Color(0xFF348F9A), Color(0xFF102D48))
    issuer.contains("IDFC", ignoreCase = true) ->
        listOf(Color(0xFF872C2F), Color(0xFFBF5452), Color(0xFF4C1F2B))
    issuer.contains("BANDHAN", ignoreCase = true) ->
        listOf(Color(0xFF6C2736), Color(0xFFAD5261), Color(0xFF391D2C))
    issuer.contains("BANK OF BARODA", ignoreCase = true) ->
        listOf(Color(0xFF85431F), Color(0xFFC07A3B), Color(0xFF4A2B22))
    else ->
        listOf(Color(0xFF32485D), Color(0xFF5E778B), Color(0xFF1B2D40))
}

/** Returns a display-safe last-four value; the original identifier is never returned. */
internal fun maskedCardNumberFromSourceId(sourceId: String?): String? {
    val suffix = sourceId?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 } ?: return null
    return "•••• $suffix"
}

private fun walletPalette(issuer: String): List<Color> = when {
    issuer.contains("FEDERAL", ignoreCase = true) ->
        listOf(Color(0xFF004CBE), Color(0xFF226FD6), Color(0xFF00265F))
    issuer.contains("HDFC", ignoreCase = true) ->
        listOf(Color(0xFF0C2D53), Color(0xFF245C8F), Color(0xFF06192F))
    issuer.contains("ICICI", ignoreCase = true) ->
        listOf(Color(0xFF241D43), Color(0xFF5E3D65), Color(0xFF17162E))
    issuer.contains("SBI", ignoreCase = true) || issuer.contains("STATE BANK", ignoreCase = true) ->
        listOf(Color(0xFF06365F), Color(0xFF1678AE), Color(0xFF09284B))
    issuer.contains("AXIS", ignoreCase = true) ->
        listOf(Color(0xFF3D1C35), Color(0xFF754158), Color(0xFF231629))
    issuer.contains("KOTAK", ignoreCase = true) ->
        listOf(Color(0xFF3E1E2A), Color(0xFF733B45), Color(0xFF25141F))
    issuer.contains("PNB", ignoreCase = true) || issuer.contains("PUNJAB", ignoreCase = true) ->
        listOf(Color(0xFF3C234E), Color(0xFF704B7E), Color(0xFF21172F))
    else ->
        listOf(Color(0xFF27384A), Color(0xFF50677B), Color(0xFF172331))
}

@Composable
private fun WalletEmvChip() {
    Canvas(Modifier.width(39.dp).height(29.dp)) {
        val radius = CornerRadius(5.dp.toPx())
        drawRoundRect(Color(0xFFE6D5AF), cornerRadius = radius)
        val line = Color(0xFF7A6B50).copy(alpha = 0.76f)
        drawRoundRect(
            color = line,
            topLeft = Offset(size.width * 0.34f, 0f),
            size = Size(size.width * 0.32f, size.height),
            cornerRadius = radius,
            style = Stroke(width = 0.75.dp.toPx())
        )
        drawLine(line, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 0.75.dp.toPx())
        drawLine(line, Offset(size.width * 0.34f, 0f), Offset(size.width * 0.34f, size.height), 0.75.dp.toPx())
        drawLine(line, Offset(size.width * 0.66f, 0f), Offset(size.width * 0.66f, size.height), 0.75.dp.toPx())
    }
}
