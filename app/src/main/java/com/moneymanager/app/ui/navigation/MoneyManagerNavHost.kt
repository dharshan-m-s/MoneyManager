package com.moneymanager.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moneymanager.app.ui.accountdetail.AccountDetailScreen
import com.moneymanager.app.ui.accounts.AccountsScreen
import com.moneymanager.app.ui.accounts.AccountsTab
import com.moneymanager.app.ui.accounts.BankLinkingScreen
import com.moneymanager.app.ui.accounts.BankSelectionScreen
import com.moneymanager.app.ui.accounts.AccountSelectionScreen
import com.moneymanager.app.ui.addtransaction.AddTransactionEntryPoint
import com.moneymanager.app.ui.addtransaction.AddTransactionScreen
import com.moneymanager.app.ui.billform.BillFormScreen
import com.moneymanager.app.ui.bills.BillsScreen
import com.moneymanager.app.ui.bills.BillTypeSelectionScreen
import com.moneymanager.app.ui.budget.BudgetScreen
import com.moneymanager.app.ui.cash.CashScreen
import com.moneymanager.app.ui.creditcardform.CreditCardFormScreen
import com.moneymanager.app.ui.dashboard.DashboardScreen
import com.moneymanager.app.ui.importer.ImportScreen
import com.moneymanager.app.ui.income.IncomeScreen
import com.moneymanager.app.ui.reimbursements.ReimbursementsScreen
import com.moneymanager.app.ui.search.SearchScreen
import com.moneymanager.app.ui.settings.AboutScreen
import com.moneymanager.app.ui.settings.FeedbackScreen
import com.moneymanager.app.ui.settings.OpenSourceScreen
import com.moneymanager.app.ui.settings.SettingsScreen
import com.moneymanager.app.updater.presentation.AppUpdatesScreen
import com.moneymanager.app.ui.spendsummary.SpendSummaryScreen
import com.moneymanager.app.ui.spendsummary.CategoryTransactionsScreen
import com.moneymanager.app.ui.categories.CategoryManagerScreen
import com.moneymanager.app.ui.categories.CreateCategoryScreen
import com.moneymanager.app.ui.transfer.TransferScreen
import com.moneymanager.app.ui.transactions.TransactionsScreen
import com.moneymanager.app.ui.transactiondetail.TransactionDetailScreen
import com.moneymanager.app.ui.accountedit.AccountEditScreen
import com.moneymanager.app.data.local.entity.BillerType

@Composable
fun MoneyManagerNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun safeBack() {
        if (!navController.popBackStack()) {
            navController.navigate(Destination.Dashboard.route) {
                popUpTo(Destination.Dashboard.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Dashboard.route,
        modifier = modifier
    ) {

        composable(Destination.Dashboard.route) {
            DashboardScreen(
                onOpenDrawer = onOpenDrawer,
                onSeeAllTransactions = { navController.navigate(Destination.Transactions.route) },
                onSeeAllSpendAreas = { navController.navigate(Destination.SpendSummary.route) },
                onSeeAllBills = { navController.navigate(Destination.Bills.route) },
                onImportPrompt = { navController.navigate(Destination.Import.route) },
                onOpenBudget = { navController.navigate(Destination.Budget.route) },
                onOpenSearch = { navController.navigate(Destination.Search.route) },
                onAddAccountIncome = {
                    navController.navigate(Destination.AccountSelection.route("ACCOUNT_INCOME"))
                },
                onAddAccountSpend = {
                    navController.navigate(Destination.AccountSelection.route("ACCOUNT_SPEND"))
                },
                onAddCashIncome = {
                    navController.navigate(Destination.Cash.route)
                },
                onAddCashSpend = {
                    navController.navigate(Destination.Cash.route)
                },
                onOpenCash = { navController.navigate(Destination.Cash.route) }
            )
        }

        composable(Destination.PersonalLoan.route) {
            AccountsScreen(
                onBack = { safeBack() },
                initialTab = AccountsTab.LOANS,
                onAccountClick = { id -> navController.navigate(Destination.AccountDetail.route(id)) },
                onAddAccount = { navController.navigate(Destination.BankSelection.route) }
            )
        }
        composable(Destination.Accounts.route) {
            AccountsScreen(
                onBack = { safeBack() },
                onAccountClick = { id -> navController.navigate(Destination.AccountDetail.route(id)) },
                onAddCreditCard = { navController.navigate(Destination.CreditCardForm.route(null)) },
                onAddAccount = { navController.navigate(Destination.BankSelection.route) },
                onAccountSettings = { navController.navigate(Destination.Settings.route) },
                onEditAccount = { id -> navController.navigate(Destination.AccountEdit.route(id)) }
            )
        }
        composable(Destination.Bills.route) {
            BillsScreen(
                onBack = { safeBack() },
                onAddBiller = { navController.navigate(Destination.BillForm.route(null)) },
                onEditBiller = { id -> navController.navigate(Destination.BillForm.route(id)) },
                onAddAccountIncome = {
                    navController.navigate(Destination.AccountSelection.route("ACCOUNT_INCOME"))
                },
                onAddAccountSpend = {
                    navController.navigate(Destination.AccountSelection.route("ACCOUNT_SPEND"))
                },
                onAddCashIncome = {
                    navController.navigate(Destination.AddTransaction.route("CASH_INCOME", null))
                },
                onAddCashSpend = {
                    navController.navigate(Destination.AddTransaction.route("CASH_SPEND", null))
                }
            )
        }
        composable(Destination.Transactions.route) {
            TransactionsScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> navController.navigate(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(Destination.Income.route) {
            IncomeScreen(
                onBack = { safeBack() },
                onTransactionClick = { id -> navController.navigate(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(
            route = Destination.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: return@composable
            TransactionDetailScreen(
                transactionId = transactionId,
                onBack = { safeBack() }
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
                onEditCash = { id -> navController.navigate(Destination.AccountEdit.route(id)) },
                onAddCashIncome = { acctId ->
                    navController.navigate(Destination.AddTransaction.route("CASH_INCOME", acctId))
                },
                onAddCashSpend = { acctId ->
                    navController.navigate(Destination.AddTransaction.route("CASH_SPEND", acctId))
                }
            )
        }
        composable(Destination.SpendSummary.route) {
            SpendSummaryScreen(onBack = { safeBack() }, onCategoryClick = { id -> navController.navigate(Destination.CategoryTransactions.route(id)) })
        }
        composable(Destination.Categories.route) {
            CategoryManagerScreen(
                onBack = { safeBack() },
                onCreateCategory = { navController.navigate(Destination.CreateCategory.route) }
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
                    navController.navigate(Destination.Transfer.route)
                },
                onCreateCategory = { navController.navigate(Destination.CreateCategory.route) }
            )
        }
        composable(Destination.CreateCategory.route) {
            CreateCategoryScreen(
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }
        composable(Destination.CategoryTransactions.route, arguments = listOf(navArgument("categoryId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("categoryId") ?: -1L
            CategoryTransactionsScreen(categoryId = id, onBack = { safeBack() }, onTransactionClick = { tx -> navController.navigate(Destination.TransactionDetail.route(tx)) })
        }
        composable(Destination.Reimbursements.route) {
            ReimbursementsScreen(onBack = { safeBack() })
        }
        composable(Destination.Settings.route) {
            SettingsScreen(
                onBack = { safeBack() },
                onImportStatement = { navController.navigate(Destination.Import.route) },
                onBudget = { navController.navigate(Destination.Budget.route) },
                onAppUpdates = { navController.navigate(Destination.AppUpdates.route) }
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
                onTransactionClick = { id -> navController.navigate(Destination.TransactionDetail.route(id)) }
            )
        }

        composable(Destination.Budget.route) {
            BudgetScreen(onClose = { safeBack() })
        }

        composable(Destination.Transfer.route) {
            TransferScreen(
                onBack = { safeBack() },
                onSaved = { safeBack() }
            )
        }

        composable(Destination.BankSelection.route) {
            BankSelectionScreen(
                onBack = { safeBack() },
                onBankSelected = { bankName ->
                    navController.navigate(Destination.BankLinking.route(bankName))
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
                    navController.navigate("bill_form?billId=-1&billType=${billerType.name}")
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
                onEditAccount = { id -> navController.navigate(Destination.AccountEdit.route(id)) },
                onAddAccountIncome = {
                    navController.navigate(Destination.AddTransaction.route("ACCOUNT_INCOME", accountId))
                },
                onAddAccountSpend = {
                    navController.navigate(Destination.AddTransaction.route("ACCOUNT_SPEND", accountId))
                },
                onAddCashIncome = {
                    navController.navigate(Destination.AddTransaction.route("CASH_INCOME", null))
                },
                onAddCashSpend = {
                    navController.navigate(Destination.AddTransaction.route("CASH_SPEND", null))
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
                onSaved = { safeBack() }
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
                onNavigateToTransfer = { navController.navigate(Destination.Transfer.route) },
                onMoreCategories = { navController.navigate(Destination.CategoryPicker.route) },
                selectedCategoryIdFromPicker = selectedCategoryId,
                onConsumeCategorySelection = { backStackEntry.savedStateHandle["categorySelection"] = null }
            )
        }
    }
}
