package com.moneymanager.app.ui.modern.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.moneymanager.app.ui.navigation.Destination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moneymanager.app.ui.modern.screens.accountdetail.AccountDetailScreen
import com.moneymanager.app.ui.modern.screens.accounts.AccountsScreen
import com.moneymanager.app.ui.accounts.AccountsTab
import com.moneymanager.app.ui.modern.screens.accounts.BankLinkingScreen
import com.moneymanager.app.ui.modern.screens.accounts.BankSelectionScreen
import com.moneymanager.app.ui.modern.screens.accounts.AccountSelectionScreen
import com.moneymanager.app.ui.addtransaction.AddTransactionEntryPoint
import com.moneymanager.app.ui.modern.screens.addtransaction.AddTransactionScreen
import com.moneymanager.app.ui.modern.screens.billform.BillFormScreen
import com.moneymanager.app.ui.modern.screens.bills.BillsScreen
import com.moneymanager.app.ui.modern.screens.bills.BillTypeSelectionScreen
import com.moneymanager.app.ui.modern.screens.budget.BudgetScreen
import com.moneymanager.app.ui.modern.screens.cash.CashScreen
import com.moneymanager.app.ui.modern.screens.creditcardform.CreditCardFormScreen
import com.moneymanager.app.ui.dashboard.DashboardBreakdownKind
import com.moneymanager.app.ui.modern.screens.dashboard.DashboardBreakdownScreen
import com.moneymanager.app.ui.modern.screens.dashboard.DashboardScreen
import com.moneymanager.app.ui.modern.screens.importer.ImportScreen
import com.moneymanager.app.ui.modern.screens.income.IncomeScreen
import com.moneymanager.app.ui.modern.screens.reimbursements.ReimbursementsScreen
import com.moneymanager.app.ui.modern.screens.search.SearchScreen
import com.moneymanager.app.ui.modern.screens.settings.AboutScreen
import com.moneymanager.app.ui.modern.screens.settings.FeedbackScreen
import com.moneymanager.app.ui.modern.screens.settings.OpenSourceScreen
import com.moneymanager.app.ui.modern.screens.settings.SettingsScreen
import com.moneymanager.app.ui.modern.screens.updater.AppUpdatesScreen
import com.moneymanager.app.ui.modern.screens.spendsummary.SpendSummaryScreen
import com.moneymanager.app.ui.modern.screens.spendsummary.CategoryTransactionsScreen
import com.moneymanager.app.ui.modern.screens.categories.CategoryEditorScreen
import com.moneymanager.app.ui.modern.screens.categories.CategoryManagerScreen
import com.moneymanager.app.ui.modern.screens.transfer.TransferScreen
import com.moneymanager.app.ui.modern.screens.transactions.TransactionsScreen
import com.moneymanager.app.ui.modern.screens.transactiondetail.TransactionDetailScreen
import com.moneymanager.app.ui.modern.screens.transactionedit.TransactionEditScreen
import com.moneymanager.app.ui.modern.screens.accountedit.AccountEditScreen
import com.moneymanager.app.data.local.entity.BillerType

@Composable
fun ModernNavHost(
    navController: NavHostController,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One Back action removes exactly one layer. The Dashboard fallback is used ONLY when the
    // controller genuinely has no previous entry (e.g. deep link/process restore); it is never
    // the normal path for a nested screen.
    fun safeBack() {
        val currentRoute = navController.currentDestination?.route
        if (currentRoute == Destination.Dashboard.route) return
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
            return
        }
        navController.navigate(Destination.Dashboard.route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun go(route: String) {
        // launchSingleTop guards against duplicate navigations from a double tap.
        navController.navigate(route) { launchSingleTop = true }
    }

    // Deleting an account/credit card is an intentional completed operation, not a Back: it must
    // return to the Accounts list rather than leave the stack on a detail/editor screen that now
    // references a deleted row. Everything above the list (the editor, and any stale detail) is
    // popped in one step. If the list is not on the stack (deep link), we open it.
    fun returnToAccountsAfterDelete() {
        val returned = navController.popBackStack(
            route = Destination.Accounts.route,
            inclusive = false
        )
        if (!returned) {
            navController.navigate(Destination.Accounts.route) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Dashboard.route,
        modifier = modifier
    ) {

        composable(Destination.Dashboard.route) {
            DashboardScreen(
                onOpenMore = onOpenMore,
                onSeeAllTransactions = { go(Destination.Transactions.route) },
                onSeeAllSpendAreas = { go(Destination.SpendSummary.route) },
                onSeeAllBills = { go(Destination.Bills.route) },
                onImportPrompt = { go(Destination.Import.route) },
                onOpenBudget = { go(Destination.Budget.route) },
                onOpenSearch = { go(Destination.Search.route) },
                onAddAccountIncome = {
                    go(Destination.AccountSelection.route("ACCOUNT_INCOME"))
                },
                onAddAccountSpend = {
                    go(Destination.AccountSelection.route("ACCOUNT_SPEND"))
                },
                onAddCashIncome = {
                    go(Destination.Cash.route)
                },
                onAddCashSpend = {
                    go(Destination.Cash.route)
                },
                onOpenCash = { go(Destination.Cash.route) },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) },
                onOpenBreakdown = { kind -> go(Destination.DashboardBreakdown.route(kind)) }
            )
        }

        composable(
            Destination.DashboardBreakdown.route,
            arguments = listOf(navArgument("kind") { type = NavType.StringType })
        ) { entry ->
            val kind = entry.arguments?.getString("kind")?.let { raw ->
                runCatching { DashboardBreakdownKind.valueOf(raw) }.getOrNull()
            } ?: DashboardBreakdownKind.BALANCE
            DashboardBreakdownScreen(
                kind = kind,
                onBack = { safeBack() },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(Destination.PersonalLoan.route) {
            AccountsScreen(
                onBack = { safeBack() },
                initialTab = AccountsTab.LOANS,
                onAccountClick = { id -> go(Destination.AccountDetail.route(id)) },
                onAddAccount = { go(Destination.BankSelection.route) }
            )
        }
        composable(Destination.Accounts.route) {
            AccountsScreen(
                onBack = { safeBack() },
                onAccountClick = { id -> go(Destination.AccountDetail.route(id)) },
                onAddCreditCard = { go(Destination.CreditCardForm.route(null)) },
                onAddAccount = { go(Destination.BankSelection.route) },
                onAccountSettings = { go(Destination.Settings.route) },
                onEditAccount = { id -> go(Destination.AccountEdit.route(id)) },
                onEditCreditCard = { id -> go(Destination.CreditCardForm.route(id)) },
                // The dock's central + opens the account actions here (see ModernRoot),
                // so the inline add row would be a second competing Add control.
                showInlineAdd = false
            )
        }
        composable(Destination.Bills.route) {
            BillsScreen(
                onBack = { safeBack() },
                // The "Bill & EMI Type" picker runs first so the form opens on the right template.
                onAddBiller = { go(Destination.BillTypeSelection.route) },
                onEditBiller = { id -> go(Destination.BillForm.route(id)) },
                onAddAccountIncome = {
                    go(Destination.AccountSelection.route("ACCOUNT_INCOME"))
                },
                onAddAccountSpend = {
                    go(Destination.AccountSelection.route("ACCOUNT_SPEND"))
                },
                onAddCashIncome = {
                    go(Destination.AddTransaction.route("CASH_INCOME", null))
                },
                onAddCashSpend = {
                    go(Destination.AddTransaction.route("CASH_SPEND", null))
                }
            )
        }
        composable(Destination.Transactions.route) {
            TransactionsScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(Destination.Income.route) {
            IncomeScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(
            route = Destination.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: return@composable
            TransactionDetailScreen(
                transactionId = transactionId,
                onBack = { safeBack() },
                onEdit = { id -> go(Destination.TransactionEdit.route(id)) },
                onDeleted = { safeBack() }
            )
        }

        composable(
            route = Destination.TransactionEdit.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: return@composable
            TransactionEditScreen(
                transactionId = transactionId,
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }

        composable(
            route = Destination.AccountEdit.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            AccountEditScreen(
                accountId = accountId,
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }

        composable(Destination.Cash.route) {
            CashScreen(
                onBack = { safeBack() },
                onEditCash = { id -> go(Destination.AccountEdit.route(id)) },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) },
                onAddCashIncome = { acctId ->
                    go(Destination.AddTransaction.route("CASH_INCOME", acctId))
                },
                onAddCashSpend = { acctId ->
                    go(Destination.AddTransaction.route("CASH_SPEND", acctId))
                }
            )
        }
        composable(Destination.SpendSummary.route) {
            SpendSummaryScreen(onBack = { safeBack() }, onCategoryClick = { id -> go(Destination.CategoryTransactions.route(id)) })
        }
        composable(Destination.Categories.route) {
            CategoryManagerScreen(
                onBack = { safeBack() },
                onCreateCategory = { go(Destination.CategoryEditor.route()) },
                onEditCategory = { id -> go(Destination.CategoryEditor.route(id)) }
            )
        }
        composable(Destination.CategoryPicker.route) {
            CategoryManagerScreen(
                onBack = { safeBack() },
                onCategorySelected = { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("categorySelection", id)
                    safeBack()
                },
                onTransferSelected = {
                    safeBack()
                    go(Destination.Transfer.route())
                },
                onCreateCategory = { go(Destination.CategoryEditor.route()) }
            )
        }
        composable(
            Destination.CategoryEditor.route,
            arguments = listOf(
                navArgument("categoryId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("categoryId") ?: -1L
            CategoryEditorScreen(
                categoryId = id.takeIf { it > 0L },
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }
        composable(Destination.CategoryTransactions.route, arguments = listOf(navArgument("categoryId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("categoryId") ?: -1L
            CategoryTransactionsScreen(categoryId = id, onBack = { safeBack() }, onTransactionClick = { tx -> go(Destination.TransactionDetail.route(tx)) })
        }
        composable(Destination.Reimbursements.route) {
            ReimbursementsScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) }
            )
        }
        composable(Destination.Settings.route) {
            SettingsScreen(
                onBack = { safeBack() },
                onImportStatement = { go(Destination.Import.route) },
                onBudget = { go(Destination.Budget.route) },
                onAppUpdates = { go(Destination.AppUpdates.route) }
            )
        }
        composable(Destination.AppUpdates.route) { AppUpdatesScreen(onBack = { safeBack() }) }

        composable(Destination.Feedback.route) { FeedbackScreen(onBack = { safeBack() }) }
        composable(Destination.OpenSource.route) { OpenSourceScreen(onBack = { safeBack() }) }
        composable(Destination.About.route) { AboutScreen(onBack = { safeBack() }) }

        composable(Destination.Import.route) {
            ImportScreen(
                onBack = { safeBack() },
                onImportComplete = {
                    navController.navigate(Destination.Dashboard.route) {
                        popUpTo(Destination.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Destination.Search.route) {
            SearchScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(Destination.Budget.route) {
            BudgetScreen(onClose = { safeBack() })
        }

        composable(
            route = Destination.Transfer.route,
            arguments = listOf(navArgument("toAccountId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val toAccountId = backStackEntry.arguments?.getLong("toAccountId") ?: -1L
            TransferScreen(
                onBack = { safeBack() },
                onSaved = { safeBack() },
                preselectToAccountId = toAccountId.takeIf { it >= 0 }
            )
        }

        composable(Destination.BankSelection.route) {
            BankSelectionScreen(
                onBack = { safeBack() },
                onBankSelected = { bankName ->
                    go(Destination.BankLinking.route(bankName))
                }
            )
        }

        composable(
            route = Destination.BankLinking.route,
            arguments = listOf(navArgument("bankName") { type = NavType.StringType })
        ) { backStackEntry ->
            val bankName = backStackEntry.arguments?.getString("bankName") ?: "Bank"
            BankLinkingScreen(
                bankName = bankName,
                onBack = { safeBack() },
                onSaved = {
                    safeBack()
                }
            )
        }

        composable(Destination.BillTypeSelection.route) {
            BillTypeSelectionScreen(
                onBack = { safeBack() },
                onBillTypeSelected = { billerType ->
                    go(Destination.BillForm.route(null, billerType.name))
                }
            )
        }

        composable(
            route = Destination.AccountDetail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: return@composable
            AccountDetailScreen(
                accountId = accountId,
                onBack = { safeBack() },
                onEditAccount = { id -> go(Destination.AccountEdit.route(id)) },
                onEditCreditCard = { id -> go(Destination.CreditCardForm.route(id)) },
                onTransactionClick = { id -> go(Destination.TransactionDetail.route(id)) },
                onAddAccountIncome = {
                    go(Destination.AddTransaction.route("ACCOUNT_INCOME", accountId))
                },
                onAddAccountSpend = {
                    go(Destination.AddTransaction.route("ACCOUNT_SPEND", accountId))
                },
                onAddCashIncome = {
                    go(Destination.AddTransaction.route("CASH_INCOME", null))
                },
                onAddCashSpend = {
                    go(Destination.AddTransaction.route("CASH_SPEND", null))
                },
                onPayCard = {
                    go(Destination.Transfer.route(accountId))
                }
            )
        }

        composable(
            route = Destination.CreditCardForm.route,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: -1L
            CreditCardFormScreen(
                editingAccountId = accountId.takeIf { it >= 0 },
                onBack = { safeBack() },
                onSaved = { safeBack() },
                onDeleted = { returnToAccountsAfterDelete() }
            )
        }

        composable(
            route = Destination.BillForm.route,
            arguments = listOf(
                navArgument("billId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("billType") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val billId = backStackEntry.arguments?.getLong("billId") ?: -1L
            val billType = backStackEntry.arguments?.getString("billType").orEmpty()
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { BillerType.valueOf(it) }.getOrNull() }
            BillFormScreen(
                editingBillId = billId.takeIf { it >= 0 },
                initialBillerType = billType,
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }

        composable(
            route = Destination.AccountSelection.route,
            arguments = listOf(navArgument("entryPoint") { type = NavType.StringType })
        ) { backStackEntry ->
            val entryPoint = backStackEntry.arguments?.getString("entryPoint") ?: "ACCOUNT_SPEND"
            val title = if (entryPoint == "ACCOUNT_INCOME") "Choose Account for Income" else "Choose Account for Spend"
            AccountSelectionScreen(
                title = title,
                onBack = { safeBack() },
                onAccountSelected = { accountId ->
                    navController.navigate(Destination.AddTransaction.route(entryPoint, accountId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Destination.AddTransaction.route,
            arguments = listOf(
                navArgument("entryPoint") { type = NavType.StringType },
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val entryPointArg = backStackEntry.arguments?.getString("entryPoint") ?: "ACCOUNT_SPEND"
            val accountIdArg = backStackEntry.arguments?.getLong("accountId") ?: -1L
            val entryPoint = when (entryPointArg) {
                "ACCOUNT_INCOME" -> AddTransactionEntryPoint.ACCOUNT_INCOME
                "ACCOUNT_SPEND" -> AddTransactionEntryPoint.ACCOUNT_SPEND
                "CASH_INCOME" -> AddTransactionEntryPoint.CASH_INCOME
                "CASH_SPEND" -> AddTransactionEntryPoint.CASH_SPEND
                else -> AddTransactionEntryPoint.ACCOUNT_SPEND
            }
            val selectionFlow = backStackEntry.savedStateHandle.getStateFlow<Long?>("categorySelection", null)
            val selectedCategoryId by selectionFlow.collectAsState()
            AddTransactionScreen(
                entryPoint = entryPoint,
                preselectedAccountId = accountIdArg.takeIf { it >= 0 },
                onBack = { safeBack() },
                onSaved = {
                    val chooserRoute = Destination.AccountSelection.route(entryPointArg)
                    if (navController.previousBackStackEntry?.destination?.route == chooserRoute) {
                        navController.popBackStack(chooserRoute, inclusive = true)
                    } else {
                        safeBack()
                    }
                },
                onNavigateToTransfer = { go(Destination.Transfer.route()) },
                onMoreCategories = { go(Destination.CategoryPicker.route) },
                selectedCategoryIdFromPicker = selectedCategoryId,
                onConsumeCategorySelection = { backStackEntry.savedStateHandle["categorySelection"] = null }
            )
        }
    }
}
