package com.moneymanager.app.data.local.converter

import androidx.room.TypeConverter
import com.moneymanager.app.data.local.entity.AccountBalanceSource
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.data.local.entity.BillingCycle
import com.moneymanager.app.data.local.entity.CategoryKind
import com.moneymanager.app.data.local.entity.ImportRowStatus
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.BusinessPersonal
import com.moneymanager.app.domain.model.PaymentType
import com.moneymanager.app.domain.model.TxnKind
import com.moneymanager.app.domain.model.TxnSubType
import com.moneymanager.app.domain.model.TxnType
import timber.log.Timber

/**
 * Room type converters for the domain and entity enums, persisted as their `name` strings.
 *
 * Every enum fallback is chosen explicitly per enum so an unseen/unknown stored value can
 * never be silently mapped to a semantically meaningful one. For the financial enums this
 * matters a lot: mapping an unknown value to e.g. PaymentType.CASH or TxnSubType.EXPENSE
 * would corrupt accounting on a forward database read. Where an enum exposes a real UNKNOWN
 * sentinel it is used; otherwise a deliberately neutral default is chosen (see each call).
 */
class Converters {

    @TypeConverter fun txnTypeToString(v: TxnType): String = v.name
    @TypeConverter fun stringToTxnType(v: String): TxnType = safeEnum(v, TxnType.UNKNOWN)

    @TypeConverter fun txnSubTypeToString(v: TxnSubType): String = v.name
    @TypeConverter fun stringToTxnSubType(v: String): TxnSubType = safeEnum(v, TxnSubType.UNKNOWN)

    @TypeConverter fun txnKindToString(v: TxnKind): String = v.name
    @TypeConverter fun stringToTxnKind(v: String): TxnKind = safeEnum(v, TxnKind.UNKNOWN)

    @TypeConverter fun paymentTypeToString(v: PaymentType): String = v.name
    @TypeConverter fun stringToPaymentType(v: String): PaymentType = safeEnum(v, PaymentType.UNKNOWN)

    @TypeConverter fun businessPersonalToString(v: BusinessPersonal): String = v.name
    @TypeConverter fun stringToBusinessPersonal(v: String): BusinessPersonal = safeEnum(v, BusinessPersonal.UNKNOWN)

    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = safeEnum(v, AccountType.UNKNOWN)

    @TypeConverter fun billingCycleToString(v: BillingCycle): String = v.name
    @TypeConverter fun stringToBillingCycle(v: String): BillingCycle = safeEnum(v, BillingCycle.MONTHLY)

    @TypeConverter fun billerTypeToString(v: BillerType): String = v.name
    @TypeConverter fun stringToBillerType(v: String): BillerType = safeEnum(v, BillerType.OTHER)

    @TypeConverter fun importRowStatusToString(v: ImportRowStatus): String = v.name
    @TypeConverter fun stringToImportRowStatus(v: String): ImportRowStatus = safeEnum(v, ImportRowStatus.PROBLEM_UNRESOLVED)

    @TypeConverter fun categoryKindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToCategoryKind(v: String): CategoryKind = safeEnum(v, CategoryKind.BOTH)

    @TypeConverter fun accountBalanceSourceToString(v: AccountBalanceSource): String = v.name
    @TypeConverter fun stringToAccountBalanceSource(v: String): AccountBalanceSource = safeEnum(v, AccountBalanceSource.ESTIMATED)

    private inline fun <reified T : Enum<T>> safeEnum(value: String, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name == value }
            ?: fallback.also {
                Timber.w("Unknown enum value '$value' for ${T::class.simpleName}, falling back to ${it.name}")
            }
    }
}