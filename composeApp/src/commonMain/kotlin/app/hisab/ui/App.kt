package app.hisab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.hisab.BuildConfig
import app.hisab.data.PocketBase
import app.hisab.data.Repository
import app.hisab.data.Store
import app.hisab.data.createHttpClient
import app.hisab.data.localDevApiUrl
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object CreateGroupRoute
@Serializable object AccountRoute
@Serializable data class JoinRoute(val code: String = "")
@Serializable data class GroupRoute(val id: String)
@Serializable data class ExpenseRoute(val groupId: String, val expenseId: String? = null)
@Serializable data class SettleRoute(val groupId: String, val from: String = "", val to: String = "", val amount: Long = 0)
@Serializable data class RecurringRoute(val groupId: String)
@Serializable data class MemberRoute(val groupId: String, val memberId: String)

/** Bumped after every change so open screens reload. */
object DataVersion {
    val tick = MutableStateFlow(0)
    fun bump() {
        tick.value++
    }
}

@Composable
fun App() {
    val repository = remember {
        val url = BuildConfig.API_URL.ifBlank { localDevApiUrl }
        Repository(PocketBase(url, createHttpClient()), Store(Settings()))
    }
    HisabTheme {
        CompositionLocalProvider(LocalRepository provides repository) {
            val nav = rememberNavController()
            val invite by DeepLinks.invite.collectAsState()
            LaunchedEffect(invite) {
                val code = invite ?: return@LaunchedEffect
                DeepLinks.consume()
                nav.navigate(JoinRoute(code))
            }
            NavHost(nav, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenGroup = { nav.navigate(GroupRoute(it)) },
                        onCreateGroup = { nav.navigate(CreateGroupRoute) },
                        onJoin = { nav.navigate(JoinRoute()) },
                        onAccount = { nav.navigate(AccountRoute) },
                    )
                }
                composable<CreateGroupRoute> {
                    CreateGroupScreen(
                        onBack = { nav.popBackStack() },
                        onCreated = { id ->
                            nav.navigate(GroupRoute(id)) { popUpTo(HomeRoute) }
                        },
                    )
                }
                composable<JoinRoute> { entry ->
                    JoinScreen(
                        initialCode = entry.toRoute<JoinRoute>().code,
                        onBack = { nav.popBackStack() },
                        onJoined = { id -> nav.navigate(GroupRoute(id)) { popUpTo(HomeRoute) } },
                    )
                }
                composable<GroupRoute> { entry ->
                    val id = entry.toRoute<GroupRoute>().id
                    GroupScreen(
                        groupId = id,
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(ExpenseRoute(id)) },
                        onEditExpense = { nav.navigate(ExpenseRoute(id, it)) },
                        onSettle = { from, to, amount -> nav.navigate(SettleRoute(id, from, to, amount)) },
                        onAddRecurring = { nav.navigate(RecurringRoute(id)) },
                        onEditMember = { nav.navigate(MemberRoute(id, it)) },
                    )
                }
                composable<ExpenseRoute> { entry ->
                    val r = entry.toRoute<ExpenseRoute>()
                    ExpenseScreen(r.groupId, r.expenseId, onDone = { nav.popBackStack() })
                }
                composable<SettleRoute> { entry ->
                    val r = entry.toRoute<SettleRoute>()
                    SettleScreen(r.groupId, r.from, r.to, r.amount, onDone = { nav.popBackStack() })
                }
                composable<RecurringRoute> { entry ->
                    RecurringScreen(entry.toRoute<RecurringRoute>().groupId, onDone = { nav.popBackStack() })
                }
                composable<MemberRoute> { entry ->
                    val r = entry.toRoute<MemberRoute>()
                    MemberScreen(r.groupId, r.memberId, onDone = { nav.popBackStack() })
                }
                composable<AccountRoute> {
                    AccountScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
