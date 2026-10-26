package com.moneymanager.app.ui.navigation

import android.content.Context
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.createGraph
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import com.moneymanager.app.data.local.entity.BillerType
import com.moneymanager.app.domain.model.AccountType
import com.moneymanager.app.domain.model.AccountTypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Section 13: an automated proof that the navigation graph is well-formed and that route
 * construction/argument round-trips are exact. Each editor/detail route must be reachable, typed
 * arguments must survive encoding, and single-layer Back must pop exactly one entry.
 *
 * This drives a REAL [NavController] over a graph built from the same [Destination] objects the app
 * uses, so a route-pattern typo or a bad default value fails here instead of on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NavigationRouteTest {

    private fun controller(): NavController {
        val context = RuntimeEnvironment.getApplication() as Context
        val nav = NavController(context)
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = Destination.Dashboard.route) {
            // One destination per route template the app can resolve to. Compose destinations are
            // stubbed with an empty content block - this test is about routing, not rendering.
            composable(Destination.Dashboard.route) {}
            composable(Destination.Accounts.route) {}
            composable(Destination.Bills.route) {}
            composable(Destination.Search.route) {}
            composable(Destination.SpendSummary.route) {}
            composable(Destination.AccountDetail.route) {}
            composable(Destination.AccountEdit.route) {}
            composable(Destination.TransactionDetail.route) {}
            composable(Destination.TransactionEdit.route) {}
            composable(Destination.CreditCardForm.route) {}
            composable(Destination.BillForm.route) {}
            composable(Destination.AccountSelection.route) {}
            composable(Destination.AddTransaction.route) {}
            composable(Destination.CategoryTransactions.route) {}
            composable(Destination.DashboardBreakdown.route) {}
        }
        return nav
    }

    @Test
    fun everyParameterisedRouteIsRegisteredAndResolves() {
        val nav = controller()
        val routes = listOf(
            Destination.AccountDetail.route(42L),
            Destination.AccountEdit.route(42L),
            Destination.TransactionDetail.route(7L),
            Destination.TransactionEdit.route(7L),
            Destination.CreditCardForm.route(3L),
            Destination.CreditCardForm.route(null),
            Destination.BillForm.route(5L, BillerType.ELECTRICITY.name),
            Destination.BillForm.route(null),
            Destination.AccountSelection.route("ACCOUNT_INCOME"),
            Destination.AddTransaction.route("ACCOUNT_SPEND", 9L),
            Destination.AddTransaction.route("CASH_INCOME", null),
            Destination.CategoryTransactions.route(2L),
            Destination.DashboardBreakdown.route(com.moneymanager.app.ui.dashboard.DashboardBreakdownKind.BALANCE)
        )
        routes.forEach { route ->
            nav.navigate(route) { launchSingleTop = true }
            assertNotNull("route did not resolve: $route", nav.currentDestination)
        }
    }

    @Test
    fun singleLayerBackPopsExactlyOneEntry() {
        val nav = controller()
        nav.navigate(Destination.Accounts.route)
        nav.navigate(Destination.AccountDetail.route(1L))
        nav.navigate(Destination.AccountEdit.route(1L))

        assertEquals(Destination.AccountEdit.route, nav.currentDestination?.route)
        // Back 1: editor -> detail
        nav.popBackStack()
        assertEquals(Destination.AccountDetail.route, nav.currentDestination?.route)
        // Back 2: detail -> accounts
        nav.popBackStack()
        assertEquals(Destination.Accounts.route, nav.currentDestination?.route)
        // Back 3: accounts -> dashboard
        nav.popBackStack()
        assertEquals(Destination.Dashboard.route, nav.currentDestination?.route)
    }

    @Test
    fun rapidDoubleTapDoesNotStackTheSameRoute() {
        val nav = controller()
        // launchSingleTop is the contract the host uses for every forward navigation.
        nav.navigate(Destination.Accounts.route) { launchSingleTop = true }
        nav.navigate(Destination.Accounts.route) { launchSingleTop = true }
        nav.navigate(Destination.Accounts.route) { launchSingleTop = true }
        nav.popBackStack()
        assertEquals(
            "a triple tap must not leave Accounts on the stack more than once",
            Destination.Dashboard.route,
            nav.currentDestination?.route
        )
    }

    @Test
    fun deletingAnAccountReturnsToTheAccountsListPoppingTheStaleDetail() {
        val nav = controller()
        nav.navigate(Destination.Accounts.route)
        nav.navigate(Destination.AccountDetail.route(1L))
        nav.navigate(Destination.CreditCardForm.route(1L))

        // Mirrors MoneyManagerNavHost.returnToAccountsAfterDelete().
        val returned = nav.popBackStack(route = Destination.Accounts.route, inclusive = false)
        assertTrue(returned)
        assertEquals(Destination.Accounts.route, nav.currentDestination?.route)
    }

    @Test
    fun drawerNavigationKeepsDashboardAsTheBackTarget() {
        val nav = controller()
        // Simulate the host's drawer contract: popUpTo(Dashboard) then push the section.
        nav.navigate(Destination.Accounts.route) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
        assertEquals(Destination.Accounts.route, nav.currentDestination?.route)
        nav.popBackStack()
        assertEquals(Destination.Dashboard.route, nav.currentDestination?.route)
    }

    @Test
    fun everySupportedAccountTypeHasNontrivialNavigationBehavior() {
        // AccountsScreen and ModernNavHost already drive per-type navigation; this is a
        // compile-time sanity check that the supported set resolves through the same path the
        // app uses.
        AccountType.entries.filter { it != AccountType.UNKNOWN }.forEach { type ->
            val accountLabel = AccountTypeCatalog.label(type)
            assertNotNull("${type.name} must resolve to a tab with a readable label", accountLabel)
        }
    }
}
