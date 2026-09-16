package com.moneymanager.app.data.local.converter

import android.util.Log
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

class Converters {

    private val TAG = "Converters"

    @TypeConverter fun txnTypeToString(v: TxnType): String = v.name
    @TypeConverter fun stringToTxnType(v: String): TxnType = safeEnum(v, TxnType.entries.toTypedArray())

    @TypeConverter fun txnSubTypeToString(v: TxnSubType): String = v.name
    @TypeConverter fun stringToTxnSubType(v: String): TxnSubType = safeEnum(v, TxnSubType.entries.toTypedArray())

    @TypeConverter fun txnKindToString(v: TxnKind): String = v.name
    @TypeConverter fun stringToTxnKind(v: String): TxnKind = safeEnum(v, TxnKind.entries.toTypedArray())

    @TypeConverter fun paymentTypeToString(v: PaymentType): String = v.name
    @TypeConverter fun stringToPaymentType(v: String): PaymentType = safeEnum(v, PaymentType.entries.toTypedArray())

    @TypeConverter fun businessPersonalToString(v: BusinessPersonal): String = v.name
    @TypeConverter fun stringToBusinessPersonal(v: String): BusinessPersonal = safeEnum(v, BusinessPersonal.entries.toTypedArray())

    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = safeEnum(v, AccountType.entries.toTypedArray())

    @TypeConverter fun billingCycleToString(v: BillingCycle): String = v.name
    @TypeConverter fun stringToBillingCycle(v: String): BillingCycle = safeEnum(v, BillingCycle.entries.toTypedArray())

    @TypeConverter fun billerTypeToString(v: BillerType): String = v.name
    @TypeConverter fun stringToBillerType(v: String): BillerType = safeEnum(v, BillerType.entries.toTypedArray())

    @TypeConverter fun importRowStatusToString(v: ImportRowStatus): String = v.name
    @TypeConverter fun stringToImportRowStatus(v: String): ImportRowStatus = safeEnum(v, ImportRowStatus.entries.toTypedArray())

    @TypeConverter fun categoryKindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToCategoryKind(v: String): CategoryKind = safeEnum(v, CategoryKind.entries.toTypedArray())

    @TypeConverter fun accountBalanceSourceToString(v: AccountBalanceSource): String = v.name
    @TypeConverter fun stringToAccountBalanceSource(v: String): AccountBalanceSource = safeEnum(v, AccountBalanceSource.entries.toTypedArray())

    private inline fun <reified T : Enum<T>> safeEnum(value: String, entries: Array<T>): T {
        return entries.firstOrNull { it.name == value } ?: entries.first().also {
            Log.w(TAG, "Unknown enum value '$value' for ${T::class.simpleName}, falling back to ${it.name}")
        }
    }
}
