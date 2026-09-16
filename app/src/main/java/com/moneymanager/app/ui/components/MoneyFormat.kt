package com.moneymanager.app.ui.components

import com.moneymanager.app.domain.model.Money
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats Money using Indian numbering (lakh/crore grouping, e.g. 1,14,300) to match the
 * reference PDF's number formatting ("₹1,14,300", "₹62,21,468"), not western 3-digit grouping.
 */
object MoneyFormat {
    private val inLocale = Locale.forLanguageTag("en-IN")
    private val formatter = NumberFormat.getCurrencyInstance(inLocale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    private val formatterWithDecimals = NumberFormat.getCurrencyInstance(inLocale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun rupeesNoDecimals(money: Money): String =
        formatter.format(money.toBigDecimal()).replace("₹", "₹")

    fun rupeesWithDecimals(money: Money): String =
        formatterWithDecimals.format(money.toBigDecimal())
}
