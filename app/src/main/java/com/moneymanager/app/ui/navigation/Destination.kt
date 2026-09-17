package com.moneymanager.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/** Main navigation. Commercial/referral-only destinations from the reference app are intentionally
 *  replaced by an Open Source destination for this project. Dashboard is the launch destination. */
sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Destination("dashboard", "Dashboard", Icons.Filled.Dashboard)
    data object PersonalLoan : Destination("personal_loan", "Personal Loan", Icons.Filled.RequestQuote)
    data object Accounts : Destination("accounts", "Accounts", Icons.Filled.AccountBalance)
    data object Bills : Destination("bills", "Bills", Icons.Filled.Description)
    data object Cash : Destination("cash", "Cash", Icons.Filled.MonetizationOn)
    data object Transactions : Destination("transactions", "Transactions", Icons.Filled.Description)
    data object Income : Destination("income", "Income", Icons.Filled.MonetizationOn)
    data object SpendSummary : Destination("spend_summary", "Spend Summary", Icons.Filled.PieChart)
    data object Categories : Destination("categories", "Categories", Icons.Filled.PieChart)
    data object CategoryPicker : Destination("category_picker", "Choose Category", Icons.Filled.PieChart)
    data object CreateCategory : Destination("create_category", "Create Category", Icons.Filled.PieChart)
    data object CategoryTransactions : Destination("category_transactions/{categoryId}", "Category Transactions", Icons.Filled.Description) { fun route(categoryId: Long) = "category_transactions/$categoryId" }
    data object Reimbursements : Destination("reimbursements", "Reimbursements", Icons.Filled.SwapHoriz)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
    data object AppUpdates : Destination("app_updates", "App Updates", Icons.Filled.Settings)
    data object Feedback : Destination("feedback", "Feedback & Report Issues", Icons.Filled.Warning)
    data object OpenSource : Destination("open_source", "Open Source", Icons.Filled.Code)
    data object About : Destination("about", "About", Icons.Filled.Info)

    // Not drawer items - reached via FAB, "See All" links, or Settings.
    data object Import : Destination("import", "Import Statement", Icons.Filled.Description)
    data object Search : Destination("search", "Search", Icons.Filled.Info)
    data object Transfer : Destination("transfer", "Transfer", Icons.Filled.SwapHoriz)
    data object Budget : Destination("budget", "Monthly Budget", Icons.Filled.MonetizationOn)
    data object AddTransaction : Destination(
        "add_transaction/{entryPoint}?accountId={accountId}",
        "Add Transaction",
        Icons.Filled.MonetizationOn
    ) {
        fun route(entryPoint: String, accountId: Long?) =
            "add_transaction/$entryPoint?accountId=${accountId ?: -1L}"
    }
    data object AccountDetail : Destination("account_detail/{accountId}", "Account", Icons.Filled.AccountBalance) {
        fun route(accountId: Long) = "account_detail/$accountId"
    }
    data object AccountEdit : Destination("account_edit/{accountId}", "Edit Account", Icons.Filled.AccountBalance) {
        fun route(accountId: Long) = "account_edit/$accountId"
    }
    data object TransactionDetail : Destination("transaction_detail/{transactionId}", "Transaction", Icons.Filled.Description) {
        fun route(transactionId: Long) = "transaction_detail/$transactionId"
    }
    data object TransactionEdit : Destination("transaction_edit/{transactionId}", "Edit Transaction", Icons.Filled.Description) {
        fun route(transactionId: Long) = "transaction_edit/$transactionId"
    }
    data object CreditCardForm : Destination("credit_card_form?accountId={accountId}", "Credit Card", Icons.Filled.AccountBalance) {
        fun route(accountId: Long?) = "credit_card_form?accountId=${accountId ?: -1L}"
    }
    data object BillForm : Destination("bill_form?billId={billId}", "Biller", Icons.Filled.Description) {
        fun route(billId: Long?) = "bill_form?billId=${billId ?: -1L}"
    }
    data object AccountSelection : Destination("account_selection/{entryPoint}", "Choose Account", Icons.Filled.AccountBalance) {
        fun route(entryPoint: String) = "account_selection/$entryPoint"
    }
    data object BankSelection : Destination("bank_selection", "Select Bank", Icons.Filled.AccountBalance)
    data object BankLinking : Destination("bank_linking?bankName={bankName}", "Link Bank Account", Icons.Filled.AccountBalance) {
        fun route(bankName: String) = "bank_linking?bankName=$bankName"
    }
    data object BillTypeSelection : Destination("bill_type_selection", "Select Bill Type", Icons.Filled.Description)

    companion object {
        val drawerItems = listOf(
            PersonalLoan, Accounts, Bills, Cash, SpendSummary, Categories, Reimbursements,
            Settings, Feedback, OpenSource, About
        )
    }
}
