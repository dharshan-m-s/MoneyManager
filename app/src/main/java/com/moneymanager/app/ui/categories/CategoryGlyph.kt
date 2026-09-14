package com.moneymanager.app.ui.categories

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom geometric category glyphs. They are intentionally minimal, soft and
 * consistent instead of relying on Material's stock icon library.
 */
@Composable
fun CategoryGlyph(
    kind: CategoryIconKind,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    Canvas(modifier.size(size)) {
        val s = minOf(size.toPx(), size.toPx()) / 24f
        drawGlyph(kind, color, s)
    }
}

private fun DrawScope.drawGlyph(kind: CategoryIconKind, color: Color, s: Float) {
    fun p(v: Number) = v.toFloat() * s
    val stroke = maxOf(p(1.7f), 1.5f)
    val mid = color.copy(alpha = 0.62f)
    val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val fill = color.copy(alpha = 0.92f)


    when (kind) {
        CategoryIconKind.Transfer -> {
            drawLine(mid, Offset(p(5), p(9)), Offset(p(18), p(9)), stroke, StrokeCap.Round)
            drawLine(mid, Offset(p(15), p(6)), Offset(p(18), p(9)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(19), p(15)), Offset(p(6), p(15)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(9), p(12)), Offset(p(6), p(15)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.CreditCard -> {
            drawRoundRect(fill, topLeft = Offset(p(4), p(7)), size = androidx.compose.ui.geometry.Size(p(16), p(11)), cornerRadius = CornerRadius(p(2)))
            drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(p(5), p(8)), size = androidx.compose.ui.geometry.Size(p(14), p(3)), cornerRadius = CornerRadius(p(1)))
            drawRoundRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(p(6), p(13)), size = androidx.compose.ui.geometry.Size(p(5), p(2)), cornerRadius = CornerRadius(p(0.7f)))
        }
        CategoryIconKind.Gift -> {
            drawRoundRect(fill, Offset(p(4), p(9)), androidx.compose.ui.geometry.Size(p(16), p(10)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(12), p(6)), Offset(p(12), p(19)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(4), p(12)), Offset(p(20), p(12)), stroke, StrokeCap.Round)
            drawArc(color = Color.White.copy(alpha=.9f), startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(p(7), p(4)), size = androidx.compose.ui.geometry.Size(p(5), p(5)), style = Stroke(stroke/1.4f))
            drawArc(color = Color.White.copy(alpha=.9f), startAngle = -20f, sweepAngle = -140f, useCenter = false, topLeft = Offset(p(12), p(4)), size = androidx.compose.ui.geometry.Size(p(5), p(5)), style = Stroke(stroke/1.4f))
        }
        CategoryIconKind.Interest, CategoryIconKind.InvestmentIncome, CategoryIconKind.Investments -> {
            drawCircle(fill, radius = p(6.5f), center = Offset(p(12), p(12)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(14)), Offset(p(12), p(8)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(12), p(8)), Offset(p(16), p(13)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(16), p(13)), Offset(p(18.5f), p(9.5f)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Loan, CategoryIconKind.Finance, CategoryIconKind.OtherFinance -> {
            drawRoundRect(fill, Offset(p(4), p(7)), androidx.compose.ui.geometry.Size(p(16), p(11)), CornerRadius(p(2)))
            drawCircle(Color.White.copy(alpha=.95f), radius = p(3.0f), center = Offset(p(12), p(12.5f)))
            drawLine(fill, Offset(p(12), p(10.5f)), Offset(p(12), p(14.5f)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.95f), Offset(p(10.2f), p(12.5f)), Offset(p(13.8f), p(12.5f)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.MutualFunds -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(3)))
            drawLine(Color.White.copy(alpha=.95f), Offset(p(8), p(15)), Offset(p(8), p(10)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.95f), Offset(p(12), p(15)), Offset(p(12), p(8)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.95f), Offset(p(16), p(15)), Offset(p(16), p(6.5f)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Other, CategoryIconKind.Miscellaneous -> {
            drawCircle(fill, p(2.1f), Offset(p(7), p(12)))
            drawCircle(fill, p(2.1f), Offset(p(12), p(12)))
            drawCircle(fill, p(2.1f), Offset(p(17), p(12)))
        }
        CategoryIconKind.ProvidentFund -> {
            drawRoundRect(fill, Offset(p(5), p(6)), androidx.compose.ui.geometry.Size(p(14), p(12)), CornerRadius(p(4)))
            drawCircle(Color.White.copy(alpha=.9f), p(3), Offset(p(12), p(12)))
            drawLine(fill, Offset(p(12), p(9.5f)), Offset(p(12), p(14.5f)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Refund -> {
            drawArc(fill, 45f, 290f, false, Offset(p(4.5f), p(4.5f)), androidx.compose.ui.geometry.Size(p(15), p(15)), style = Stroke(stroke))
            drawLine(fill, Offset(p(6), p(8)), Offset(p(4.7f), p(5.8f)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(6), p(8)), Offset(p(8.3f), p(7.4f)), stroke, StrokeCap.Round)
            drawCircle(fill, p(2.4f), Offset(p(12), p(12)))
            drawLine(Color.White, Offset(p(12), p(10.5f)), Offset(p(12), p(13.5f)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Reward -> {
            drawCircle(fill, p(6), Offset(p(12), p(12)))
            drawCircle(Color.White.copy(alpha=.92f), p(3.2f), Offset(p(12), p(12)))
            drawLine(fill, Offset(p(12), p(9.5f)), Offset(p(12), p(14.5f)), stroke/1.5f, StrokeCap.Round)
            drawLine(fill, Offset(p(9.8f), p(12)), Offset(p(14.2f), p(12)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Salary, CategoryIconKind.SalaryBonus -> {
            drawRoundRect(fill, Offset(p(4), p(8)), androidx.compose.ui.geometry.Size(p(16), p(10)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(5.5f)), Offset(p(16), p(5.5f)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(10), p(8)), Offset(p(10), p(18)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(14), p(8)), Offset(p(14), p(18)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.Savings -> {
            drawRoundRect(fill, Offset(p(5), p(7)), androidx.compose.ui.geometry.Size(p(14), p(10)), CornerRadius(p(5)))
            drawCircle(Color.White.copy(alpha=.92f), p(2.5f), Offset(p(17), p(8)))
            drawLine(Color.White.copy(alpha=.92f), Offset(p(9), p(17)), Offset(p(9), p(19)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.92f), Offset(p(15), p(17)), Offset(p(15), p(19)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Selling -> {
            val path = Path().apply { moveTo(p(5), p(8)); lineTo(p(15), p(8)); lineTo(p(19), p(12)); lineTo(p(12), p(19)); lineTo(p(5), p(12)); close() }
            drawPath(path, fill)
            drawCircle(Color.White.copy(alpha=.9f), p(1.3f), Offset(p(9), p(11)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(13), p(14)), Offset(p(17), p(10)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Upi, CategoryIconKind.Mobile, CategoryIconKind.PhoneBill -> {
            drawRoundRect(fill, Offset(p(7), p(3.5f)), androidx.compose.ui.geometry.Size(p(10), p(17)), CornerRadius(p(2.5f)))
            drawCircle(Color.White.copy(alpha=.85f), p(1), Offset(p(12), p(17.5f)))
            drawLine(Color.White.copy(alpha=.85f), Offset(p(9), p(6)), Offset(p(15), p(6)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.WalletRecharge, CategoryIconKind.Cash -> {
            drawRoundRect(fill, Offset(p(4), p(7)), androidx.compose.ui.geometry.Size(p(16), p(11)), CornerRadius(p(2.5f)))
            drawRoundRect(Color.White.copy(alpha=.26f), Offset(p(5.5f), p(9)), androidx.compose.ui.geometry.Size(p(13), p(2.8f)), CornerRadius(p(1)))
            drawCircle(Color.White.copy(alpha=.92f), p(1.8f), Offset(p(16.5f), p(14)))
        }
        CategoryIconKind.Bills, CategoryIconKind.Subscription -> {
            val path = Path().apply { moveTo(p(6), p(4)); lineTo(p(18), p(4)); lineTo(p(18), p(20)); lineTo(p(15), p(18.3f)); lineTo(p(12), p(20)); lineTo(p(9), p(18.3f)); lineTo(p(6), p(20)); close() }
            drawPath(path, fill)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(9)), Offset(p(15), p(9)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(12)), Offset(p(14), p(12)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(15)), Offset(p(13), p(15)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Electricity -> {
            val path = Path().apply { moveTo(p(13), p(3)); lineTo(p(7), p(13)); lineTo(p(11.5f), p(13)); lineTo(p(10), p(21)); lineTo(p(17), p(10.5f)); lineTo(p(13), p(10.5f)); close() }
            drawPath(path, fill)
        }
        CategoryIconKind.Water -> {
            val path = Path().apply { moveTo(p(12), p(4)); cubicTo(p(7), p(9), p(6), p(12), p(6), p(14)); cubicTo(p(6), p(17.5f), p(8.8f), p(20), p(12), p(20)); cubicTo(p(15.2f), p(20), p(18), p(17.5f), p(18), p(14)); cubicTo(p(18), p(12), p(17), p(9), p(12), p(4)); close() }
            drawPath(path, fill)
            drawCircle(Color.White.copy(alpha=.35f), p(1.3f), Offset(p(10), p(12)))
        }
        CategoryIconKind.Wifi, CategoryIconKind.Broadband -> {
            drawArc(fill, 210f, 120f, false, Offset(p(4), p(4)), androidx.compose.ui.geometry.Size(p(16), p(16)), style = Stroke(stroke))
            drawArc(fill, 215f, 110f, false, Offset(p(7), p(7)), androidx.compose.ui.geometry.Size(p(10), p(10)), style = Stroke(stroke))
            drawCircle(fill, p(1.6f), Offset(p(12), p(17)))
        }
        CategoryIconKind.Travel -> {
            drawRoundRect(fill, Offset(p(6), p(5)), androidx.compose.ui.geometry.Size(p(12), p(14)), CornerRadius(p(2.5f)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(9)), Offset(p(15), p(9)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(12)), Offset(p(15), p(12)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(15)), Offset(p(13), p(15)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.TravelHotel -> {
            drawRoundRect(fill, Offset(p(5), p(7)), androidx.compose.ui.geometry.Size(p(14), p(11)), CornerRadius(p(3)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(10)), Offset(p(16), p(10)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(13)), Offset(p(13), p(13)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(16)), Offset(p(15), p(16)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.AirTicket -> {
            val path = Path().apply { moveTo(p(4), p(14)); lineTo(p(9), p(12)); lineTo(p(17.8f), p(5.5f)); lineTo(p(19.5f), p(7.2f)); lineTo(p(14.2f), p(14)); lineTo(p(20), p(15.8f)); lineTo(p(19), p(18)); lineTo(p(12), p(15)); lineTo(p(8), p(20)); lineTo(p(6.2f), p(18.2f)); lineTo(p(9.5f), p(14)); close() }
            drawPath(path, fill)
        }
        CategoryIconKind.Bike, CategoryIconKind.Cycle -> {
            drawCircle(color, p(4), Offset(p(7), p(16)), style = Stroke(stroke))
            drawCircle(color, p(4), Offset(p(17), p(16)), style = Stroke(stroke))
            drawLine(fill, Offset(p(7), p(16)), Offset(p(11), p(10)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(11), p(10)), Offset(p(15), p(16)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(7), p(16)), Offset(p(15), p(16)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(11), p(10)), Offset(p(13), p(10)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Car -> {
            drawRoundRect(fill, Offset(p(5), p(10)), androidx.compose.ui.geometry.Size(p(14), p(7)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.92f), Offset(p(8), p(10)), Offset(p(9.5f), p(7)), stroke/1.2f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.92f), Offset(p(9.5f), p(7)), Offset(p(14.5f), p(7)), stroke/1.2f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.92f), Offset(p(14.5f), p(7)), Offset(p(16), p(10)), stroke/1.2f, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha=.95f), p(1.6f), Offset(p(8), p(18)))
            drawCircle(Color.White.copy(alpha=.95f), p(1.6f), Offset(p(16), p(18)))
        }
        CategoryIconKind.Bus, CategoryIconKind.Transport -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(2.5f)))
            drawRoundRect(Color.White.copy(alpha=.24f), Offset(p(7), p(7)), androidx.compose.ui.geometry.Size(p(10), p(5)), CornerRadius(p(1)))
            drawCircle(Color.White.copy(alpha=.95f), p(1.6f), Offset(p(8), p(18)))
            drawCircle(Color.White.copy(alpha=.95f), p(1.6f), Offset(p(16), p(18)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(15)), Offset(p(15), p(15)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.Courier, CategoryIconKind.Delivery -> {
            val path = Path().apply { moveTo(p(5), p(8)); lineTo(p(12), p(4.5f)); lineTo(p(19), p(8)); lineTo(p(12), p(11.5f)); close() }
            drawPath(path, fill)
            drawRoundRect(fill, Offset(p(5), p(10)), androidx.compose.ui.geometry.Size(p(14), p(9)), CornerRadius(p(1.5f)))
            drawLine(Color.White.copy(alpha=.8f), Offset(p(12), p(11.5f)), Offset(p(12), p(19)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Grocery -> {
            drawLine(fill, Offset(p(6), p(7)), Offset(p(8), p(18)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(18), p(7)), Offset(p(16), p(18)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(7), p(17)), Offset(p(17), p(17)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(8), p(8)), Offset(p(18), p(8)), stroke, StrokeCap.Round)
            drawCircle(fill, p(1.5f), Offset(p(9), p(20)))
            drawCircle(fill, p(1.5f), Offset(p(15), p(20)))
        }
        CategoryIconKind.FastFood, CategoryIconKind.Food -> {
            drawRoundRect(fill, Offset(p(5), p(8)), androidx.compose.ui.geometry.Size(p(14), p(9)), CornerRadius(p(4)))
            drawArc(Color.White.copy(alpha=.9f), 180f, 180f, false, Offset(p(7), p(5)), androidx.compose.ui.geometry.Size(p(10), p(8)), style = Stroke(stroke/1.3f))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(7), p(13)), Offset(p(17), p(13)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.Dinner, CategoryIconKind.Breakfast, CategoryIconKind.Dining, CategoryIconKind.DiningOut -> {
            drawCircle(fill.copy(alpha=.9f), p(7), Offset(p(12), p(12)))
            drawCircle(Color.White.copy(alpha=.14f), p(4.8f), Offset(p(12), p(12)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(6), p(6)), Offset(p(6), p(18)), stroke/1.2f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(18), p(6)), Offset(p(18), p(18)), stroke/1.2f, StrokeCap.Round)
        }
        CategoryIconKind.Coffee -> {
            drawRoundRect(fill, Offset(p(6), p(8)), androidx.compose.ui.geometry.Size(p(11), p(8)), CornerRadius(p(2)))
            drawArc(Color.White.copy(alpha=.9f), -80f, 160f, false, Offset(p(14), p(9)), androidx.compose.ui.geometry.Size(p(7), p(6)), style = Stroke(stroke/1.5f))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(6), p(18)), Offset(p(18), p(18)), stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.7f), Offset(p(9), p(5)), Offset(p(9), p(7)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Books -> {
            drawRoundRect(fill, Offset(p(6), p(5)), androidx.compose.ui.geometry.Size(p(12), p(14)), CornerRadius(p(1.5f)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(9), p(7)), Offset(p(15), p(7)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.7f), Offset(p(9), p(10)), Offset(p(15), p(10)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.7f), Offset(p(9), p(13)), Offset(p(14), p(13)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Clothes -> {
            val path = Path().apply { moveTo(p(8), p(5)); lineTo(p(12), p(7)); lineTo(p(16), p(5)); lineTo(p(19), p(9)); lineTo(p(16.5f), p(11)); lineTo(p(15), p(9.5f)); lineTo(p(15), p(19)); lineTo(p(9), p(19)); lineTo(p(9), p(9.5f)); lineTo(p(7.5f), p(11)); lineTo(p(5), p(9)); close() }
            drawPath(path, fill)
            drawLine(Color.White.copy(alpha=.65f), Offset(p(10), p(5.5f)), Offset(p(14), p(5.5f)), stroke/1.6f, StrokeCap.Round)
        }
        CategoryIconKind.Shopping -> {
            drawRoundRect(fill, Offset(p(6), p(8)), androidx.compose.ui.geometry.Size(p(12), p(11)), CornerRadius(p(2)))
            drawArc(Color.White.copy(alpha=.9f), 180f, 180f, false, Offset(p(8), p(5)), androidx.compose.ui.geometry.Size(p(8), p(7)), style = Stroke(stroke/1.4f))
            drawLine(Color.White.copy(alpha=.8f), Offset(p(9), p(12)), Offset(p(15), p(12)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Beauty -> {
            drawCircle(fill, p(5.2f), Offset(p(9), p(11)))
            drawCircle(Color.White.copy(alpha=.12f), p(2.5f), Offset(p(9), p(11)))
            drawLine(fill, Offset(p(13), p(15)), Offset(p(19), p(9)), stroke*1.2f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(17), p(7)), Offset(p(17), p(11)), stroke/1.5f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(15), p(9)), Offset(p(19), p(9)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Fitness, CategoryIconKind.Sports -> {
            drawCircle(fill, p(2.5f), Offset(p(12), p(6)))
            drawLine(fill, Offset(p(12), p(9)), Offset(p(9), p(15)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(9), p(15)), Offset(p(15), p(17)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(9), p(11)), Offset(p(15), p(11)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(9), p(15)), Offset(p(7), p(20)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(15), p(17)), Offset(p(18), p(20)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Medical, CategoryIconKind.Pharmacy -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(4)))
            drawRoundRect(Color.White.copy(alpha=.95f), Offset(p(10), p(8)), androidx.compose.ui.geometry.Size(p(4), p(8)), CornerRadius(p(1)))
            drawRoundRect(Color.White.copy(alpha=.95f), Offset(p(8), p(10)), androidx.compose.ui.geometry.Size(p(8), p(4)), CornerRadius(p(1)))
        }
        CategoryIconKind.Education -> {
            val path = Path().apply { moveTo(p(4), p(9)); lineTo(p(12), p(5)); lineTo(p(20), p(9)); lineTo(p(12), p(13)); close() }
            drawPath(path, fill)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(11)), Offset(p(8), p(17)), stroke/1.4f, StrokeCap.Round)
            drawArc(Color.White.copy(alpha=.9f), 0f, 180f, false, Offset(p(8), p(14)), androidx.compose.ui.geometry.Size(p(8), p(5)), style = Stroke(stroke/1.4f))
        }
        CategoryIconKind.Movie -> {
            drawRoundRect(fill, Offset(p(5), p(6)), androidx.compose.ui.geometry.Size(p(14), p(12)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.85f), Offset(p(9), p(6)), Offset(p(9), p(18)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.85f), Offset(p(15), p(6)), Offset(p(15), p(18)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.85f), Offset(p(5), p(10)), Offset(p(19), p(10)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.Donation -> {
            drawCircle(fill, p(6.5f), Offset(p(12), p(12)))
            drawCircle(Color.White.copy(alpha=.9f), p(2.7f), Offset(p(9), p(10)))
            drawCircle(Color.White.copy(alpha=.9f), p(2.7f), Offset(p(15), p(10)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(15)), Offset(p(16), p(15)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Charity -> {
            drawCircle(fill, p(6.5f), Offset(p(12), p(12)))
            val heart = Path().apply { moveTo(p(12), p(17)); cubicTo(p(9.3f), p(14.5f), p(7.8f), p(13.2f), p(7.8f), p(11.5f)); cubicTo(p(7.8f), p(9.2f), p(10), p(8.2f), p(12), p(10)); cubicTo(p(14), p(8.2f), p(16.2f), p(9.2f), p(16.2f), p(11.5f)); cubicTo(p(16.2f), p(13.2f), p(14.7f), p(14.5f), p(12), p(17)); close() }
            drawPath(heart, Color.White.copy(alpha=.96f))
        }
        CategoryIconKind.Music -> {
            drawLine(fill, Offset(p(15), p(5)), Offset(p(15), p(16)), stroke*1.1f, StrokeCap.Round)
            drawLine(fill, Offset(p(15), p(5)), Offset(p(19), p(4)), stroke*1.1f, StrokeCap.Round)
            drawCircle(fill, p(3), Offset(p(10), p(17)))
        }
        CategoryIconKind.Nightlife -> {
            drawRoundRect(fill, Offset(p(6), p(5)), androidx.compose.ui.geometry.Size(p(12), p(15)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.85f), Offset(p(8), p(9)), Offset(p(16), p(9)), stroke/1.4f, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha=.82f), p(1.1f), Offset(p(10), p(14)))
            drawCircle(Color.White.copy(alpha=.82f), p(1.1f), Offset(p(14), p(14)))
        }
        CategoryIconKind.Pets -> {
            drawCircle(fill, p(4.5f), Offset(p(12), p(14)))
            drawCircle(fill, p(2), Offset(p(7), p(8)))
            drawCircle(fill, p(2), Offset(p(17), p(8)))
            drawCircle(fill, p(1.8f), Offset(p(5), p(13)))
            drawCircle(fill, p(1.8f), Offset(p(19), p(13)))
        }
        CategoryIconKind.Kids, CategoryIconKind.BabyCare -> {
            drawCircle(fill, p(5), Offset(p(12), p(8)))
            drawRoundRect(fill, Offset(p(7), p(13)), androidx.compose.ui.geometry.Size(p(10), p(7)), CornerRadius(p(3)))
            drawCircle(Color.White.copy(alpha=.9f), p(0.8f), Offset(p(10), p(8)))
            drawCircle(Color.White.copy(alpha=.9f), p(0.8f), Offset(p(14), p(8)))
        }
        CategoryIconKind.HouseholdGoods, CategoryIconKind.Household -> {
            drawRoundRect(fill, Offset(p(5), p(8)), androidx.compose.ui.geometry.Size(p(14), p(10)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(8)), Offset(p(8), p(5)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(16), p(8)), Offset(p(16), p(5)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(8), p(5)), Offset(p(16), p(5)), stroke/1.4f, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha=.8f), p(1.2f), Offset(p(10), p(13)))
            drawCircle(Color.White.copy(alpha=.8f), p(1.2f), Offset(p(14), p(13)))
        }
        CategoryIconKind.Home, CategoryIconKind.RentMortgage -> {
            val path = Path().apply { moveTo(p(4), p(11)); lineTo(p(12), p(4)); lineTo(p(20), p(11)); lineTo(p(18), p(11)); lineTo(p(18), p(19)); lineTo(p(6), p(19)); lineTo(p(6), p(11)); close() }
            drawPath(path, fill)
            drawRoundRect(Color.White.copy(alpha=.9f), Offset(p(10), p(13)), androidx.compose.ui.geometry.Size(p(4), p(6)), CornerRadius(p(0.8f)))
        }
        CategoryIconKind.Family -> {
            drawCircle(fill, p(3.2f), Offset(p(8), p(8)))
            drawCircle(fill, p(3.2f), Offset(p(16), p(8)))
            drawCircle(Color.White.copy(alpha=.85f), p(1.2f), Offset(p(12), p(10)))
            drawRoundRect(fill, Offset(p(5), p(12)), androidx.compose.ui.geometry.Size(p(6), p(7)), CornerRadius(p(2)))
            drawRoundRect(fill, Offset(p(13), p(12)), androidx.compose.ui.geometry.Size(p(6), p(7)), CornerRadius(p(2)))
        }
        CategoryIconKind.Electronics -> {
            drawRoundRect(fill, Offset(p(4), p(6)), androidx.compose.ui.geometry.Size(p(16), p(11)), CornerRadius(p(2)))
            drawLine(Color.White.copy(alpha=.85f), Offset(p(9), p(19)), Offset(p(15), p(19)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.85f), Offset(p(12), p(17)), Offset(p(12), p(19)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.DailyCare, CategoryIconKind.PersonalCare -> {
            drawCircle(fill, p(7), Offset(p(12), p(11)))
            val heart = Path().apply { moveTo(p(12), p(19)); cubicTo(p(9), p(16), p(6.5f), p(14.5f), p(6.5f), p(11.5f)); cubicTo(p(6.5f), p(8), p(9.5f), p(7), p(12), p(9.5f)); cubicTo(p(14.5f), p(7), p(17.5f), p(8), p(17.5f), p(11.5f)); cubicTo(p(17.5f), p(14.5f), p(15), p(16), p(12), p(19)); close() }
            drawPath(heart, Color.White.copy(alpha=.95f))
        }
        CategoryIconKind.Insurance -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(4)))
            val shield = Path().apply { moveTo(p(12), p(7)); lineTo(p(16.5f), p(9)); lineTo(p(16), p(14)); cubicTo(p(15.3f), p(16.5f), p(13.8f), p(18), p(12), p(19)); cubicTo(p(10.2f), p(18), p(8.7f), p(16.5f), p(8), p(14)); lineTo(p(7.5f), p(9)); close() }
            drawPath(shield, Color.White.copy(alpha = .92f))
            drawLine(fill, Offset(p(12), p(10)), Offset(p(12), p(15)), stroke/1.5f, StrokeCap.Round)
            drawLine(fill, Offset(p(9.8f), p(12.5f)), Offset(p(14.2f), p(12.5f)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Cigarette -> {
            drawRoundRect(fill, Offset(p(5), p(12)), androidx.compose.ui.geometry.Size(p(12), p(4)), CornerRadius(p(1.3f)))
            drawRect(Color.White.copy(alpha=.85f), Offset(p(14), p(12)), androidx.compose.ui.geometry.Size(p(3), p(4)))
            drawArc(Color.White.copy(alpha=.5f), -90f, 60f, false, Offset(p(16), p(5)), androidx.compose.ui.geometry.Size(p(4), p(5)), style = Stroke(stroke/1.6f))
        }
        CategoryIconKind.Tax -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(3)))
            drawLine(Color.White.copy(alpha=.95f), Offset(p(8), p(9)), Offset(p(16), p(9)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.95f), Offset(p(8), p(12)), Offset(p(16), p(12)), stroke/1.3f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.95f), Offset(p(8), p(15)), Offset(p(14), p(15)), stroke/1.3f, StrokeCap.Round)
        }
        CategoryIconKind.Custom -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(4)))
            drawCircle(Color.White.copy(alpha=.9f), p(1.8f), Offset(p(9), p(9)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(7), p(17)), Offset(p(12), p(12)), stroke/1.5f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(12), p(12)), Offset(p(17), p(17)), stroke/1.5f, StrokeCap.Round)
        }
        CategoryIconKind.Maintenance, CategoryIconKind.Repair -> {
            drawCircle(fill, p(6), Offset(p(12), p(12)))
            drawRoundRect(Color.White.copy(alpha=.92f), Offset(p(8), p(9)), androidx.compose.ui.geometry.Size(p(8), p(3)), CornerRadius(p(1)))
            drawLine(Color.White.copy(alpha=.92f), Offset(p(12), p(7)), Offset(p(12), p(17)), stroke/1.4f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.92f), Offset(p(8), p(12)), Offset(p(16), p(12)), stroke/1.4f, StrokeCap.Round)
        }
        CategoryIconKind.Toys -> {
            drawRoundRect(fill, Offset(p(5), p(7)), androidx.compose.ui.geometry.Size(p(14), p(10)), CornerRadius(p(3)))
            drawCircle(Color.White.copy(alpha=.92f), p(2.1f), Offset(p(9), p(12)))
            drawCircle(Color.White.copy(alpha=.92f), p(15), Offset(p(12), p(12)), style = Stroke(stroke/1.5f))
        }
        CategoryIconKind.Entertainment -> {
            drawRoundRect(fill, Offset(p(5), p(6)), androidx.compose.ui.geometry.Size(p(14), p(12)), CornerRadius(p(4)))
            drawCircle(Color.White.copy(alpha=.9f), p(1.5f), Offset(p(10), p(12)))
            drawLine(Color.White.copy(alpha=.9f), Offset(p(14), p(12)), Offset(p(17), p(12)), stroke/1.6f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha=.9f), Offset(p(15.5f), p(10.5f)), Offset(p(15.5f), p(13.5f)), stroke/1.6f, StrokeCap.Round)
        }
        CategoryIconKind.Gas -> {
            drawRoundRect(fill, Offset(p(6), p(7)), androidx.compose.ui.geometry.Size(p(10), p(13)), CornerRadius(p(2)))
            drawCircle(Color.White.copy(alpha=.88f), p(2.1f), Offset(p(11), p(12)))
            drawLine(fill, Offset(p(16), p(9)), Offset(p(19), p(12)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(19), p(12)), Offset(p(19), p(19)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Fuel -> {
            drawRoundRect(fill, Offset(p(6), p(6)), androidx.compose.ui.geometry.Size(p(10), p(14)), CornerRadius(p(2)))
            drawRoundRect(Color.White.copy(alpha=.85f), Offset(p(9), p(9)), androidx.compose.ui.geometry.Size(p(4), p(4)), CornerRadius(p(0.8f)))
            drawLine(fill, Offset(p(16), p(9)), Offset(p(19), p(12)), stroke, StrokeCap.Round)
            drawLine(fill, Offset(p(19), p(12)), Offset(p(19), p(18)), stroke, StrokeCap.Round)
        }
        CategoryIconKind.Parking -> {
            drawRoundRect(fill, Offset(p(5), p(5)), androidx.compose.ui.geometry.Size(p(14), p(14)), CornerRadius(p(3)))
            drawLine(Color.White.copy(alpha=.92f), Offset(p(10), p(8)), Offset(p(10), p(16)), stroke/1.3f, StrokeCap.Round)
            drawArc(Color.White.copy(alpha=.92f), -90f, 180f, false, Offset(p(10), p(8)), androidx.compose.ui.geometry.Size(p(5), p(5)), style = Stroke(stroke/1.3f))
        }
    }
}
