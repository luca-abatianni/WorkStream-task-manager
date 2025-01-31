package it.polito.workstream

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import it.polito.workstream.ui.login.LoginActivity
import it.polito.workstream.ui.models.Task
import it.polito.workstream.ui.models.Team
import it.polito.workstream.ui.models.User
import it.polito.workstream.ui.screens.chats.*
import it.polito.workstream.ui.screens.tasks.*
import it.polito.workstream.ui.screens.tasks.components.ShowTaskDetails
import it.polito.workstream.ui.screens.team.ConfirmJoinTeamPage
import it.polito.workstream.ui.screens.team.NoTeamsScreen
import it.polito.workstream.ui.screens.team.TeamScreen
import it.polito.workstream.ui.screens.userprofile.UserScreen
import it.polito.workstream.ui.shared.*
import it.polito.workstream.ui.theme.WorkStreamTheme
import it.polito.workstream.ui.viewmodels.TaskViewModel
import it.polito.workstream.ui.viewmodels.TeamListViewModel
import it.polito.workstream.ui.viewmodels.TeamViewModel
import it.polito.workstream.ui.viewmodels.UserViewModel
import it.polito.workstream.ui.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WorkStreamTheme {
                val currentUser by rememberUpdatedState(newValue = Firebase.auth.currentUser)
                val context = LocalContext.current
                val app = context.applicationContext as MainApplication
                val scope = rememberCoroutineScope()
                val token = stringResource(it.polito.workstream.R.string.web_client_id)

                var user by remember { mutableStateOf<User?>(null) }

                if (currentUser == null) {
                    // Redirect to LoginActivity if the user is not authenticated
                    LaunchedEffect(Unit) {
                        val loginIntent = Intent(context, LoginActivity::class.java)
                        context.startActivity(loginIntent)

                        finish() // Finish MainActivity so the user cannot go back to it
                    }
                } else {
                    LaunchedEffect(currentUser) {
                        checkOrCreateUserInFirestore(currentUser!!) { retrievedUser ->
                            user = retrievedUser
                            app._user.value = retrievedUser
                            Log.d("user", retrievedUser.toString())
                            if (!retrievedUser.activeTeam.isNullOrEmpty())
                                app.activeTeamId.value = retrievedUser.activeTeam!!

                        }
                    }
                }

                if (user != null) {
                    ContentView {

                        // Pass logout callback
                        scope.launch {
                            performLogout(context, app, token)
                        }
                    }
                } else {
                    // Show a loading screen or similar while the user is being initialized
                    LoadingScreen()
                }
            }
        }
    }

    private fun checkOrCreateUserInFirestore(firebaseUser: FirebaseUser, onComplete: (User) -> Unit) {
        Log.d("firebaseUser.email", "${firebaseUser.email}")
        val userRef = firebaseUser.email?.let { db.collection("users").document(it) }
        userRef?.get()?.addOnSuccessListener { document ->
            if (document.exists()) {
                // Ottieni i dati dal documento
                val firstName = document.getString("firstName") ?: ""
                val lastName = document.getString("lastName") ?: ""
                val email = document.getString("email") ?: ""
                val location = document.getString("location")
                var activeTeam = document.getString("activeTeam")
                val teams = document.get("teams") as? MutableList<String> ?: mutableListOf()
                if (activeTeam.isNullOrEmpty() && teams.isNotEmpty()) {
                    activeTeam = teams[0]
                } else if (activeTeam.isNullOrEmpty() && teams.isEmpty()) {
                    activeTeam = "no_team"
                }

                Log.d("UserData", "First Name: $firstName")
                Log.d("UserData", "Last Name: $lastName")
                Log.d("UserData", "Email: $email")
                Log.d("UserData", "Location: $location")
                Log.d("UserData", "Active Team: $activeTeam")
                Log.d("UserData", "Teams: $teams")

                // Crea l'oggetto User
                val user = User(firstName = firstName, lastName = lastName, email = email, location = location, profilePicture = firebaseUser.photoUrl.toString(), activeTeam = activeTeam, teams = teams)

                // Completa l'operazione con il callback
                onComplete(user)
            } else {
                Log.d("ERROR", "User not signed in yet")
                onComplete(User()) // Return a default user object if not found
            }
        }?.addOnFailureListener { e ->
            Log.e("ERROR", "Error fetching user document", e)
            onComplete(User()) // Return a default user object on error
        }
    }

    private fun performLogout(context: Context, app: MainApplication, token: String) {
        Firebase.auth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(token)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut()
            .addOnSuccessListener { Log.d("Google", "Google sign out successful") }
            .addOnFailureListener { Log.w("Google", "Google sign out failure") }

        app._user.value = User()
        //delay(1000)
        val loginIntent = Intent(context, MainActivity::class.java)
        loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(loginIntent)
        Log.d("Merda", "sono finito")
        finish()
    }
}

@Composable
fun LoadingScreen() {
    // You can customize this with a proper loading indicator
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // For example, a CircularProgressIndicator in the center of the screen
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ContentView(
    vm: TeamListViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    teamVM: TeamViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    taskVM: TaskViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    userVM: UserViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    onLogout: () -> Unit
) {

    val activeTeamId = vm.activeTeamId.collectAsState().value //activeTeam.id //vm.activeTeam.collectAsState().value.id
    val activeTeam = vm.fetchActiveTeam(activeTeamId).collectAsState(null).value ?: Team(id = "no_team", name = "", admin = "")
    val teamMembers = vm.fetchUsers(activeTeamId).collectAsState(initial = listOf()).value
    val tasksList = vm.getTasks(activeTeamId).collectAsState(initial = listOf())//vm.teamTasks.collectAsState(initial = emptyList())
    val sections = activeTeam.sections
    val user by vm.user.collectAsState()
    Log.d("activeTeam", activeTeam.name)
    Log.d("activeTeamId", activeTeamId)

    val navController = rememberNavController()

    var canNavigateBack: Boolean by remember { mutableStateOf(false) }
    navController.addOnDestinationChangedListener { controller, _, _ ->
        canNavigateBack = controller.previousBackStackEntry != null
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val onItemSelect: (
        route: Int,
        taskId: String?,
        taskName: String?,
        userId: Long?,
        userMail: String?
    ) -> Unit = { route: Int, taskId: String?, taskName: String?, userId: Long?, userMail: String? ->

        val routeName = when (route) {
            1 -> when (taskId) {
                null -> "/${activeTeamId}/${Route.TeamTasks.name}"
                else -> "${Route.TeamTasks.name}/${taskId}"
            }

            2 -> Route.MyTasks.name
            3 -> Route.NewTask.name
            4 -> when (taskId) {
                null -> Route.TeamTasks.name
                else -> "${Route.EditTask.name}/${taskId}"
            }

            5 -> Route.TeamScreen.name
            6 -> when (taskId) { //riutilizzo taskId per passare 'id dello user da visualizzare
                null -> Route.TeamScreen.name
                else -> "${Route.UserView.name}/${taskId}"
            }

            7 -> Route.Back.name
            8 -> when {
                userId == null && userMail == null -> Route.ChatScreen.name
                userId == (-1).toLong() && userMail == null -> "${Route.ChatScreen.name}/group"
                userId == null && userMail != null -> "${Route.ChatScreen.name}/${userMail}"
                else -> Route.ChatScreen.name
            }

            9 -> Route.NewChat.name

            10 -> Route.UserView.name

            else -> Route.TeamScreen.name
        }

        //vm.setActivePage( taskName ?: routeName)
        if (routeName != Route.Back.name)
            navController.navigate(route = routeName)
        else
            navController.popBackStack()
    }

    val app = (LocalContext.current.applicationContext as? MainApplication) ?: throw IllegalArgumentException("Bad Application class")

    val navigateTo = { s: String ->
        if (s != Route.Back.name) navController.navigate(s) else navController.popBackStack()
    }

    NavDrawer(navigateTo = navigateTo, drawerState = drawerState, activeUser = app.user) {
        Scaffold(
            topBar = { Column { TopBarWrapper(drawerState = drawerState, navigateTo = navigateTo) } },
            bottomBar = { BottomNavbarWrapper(navigateTo = navigateTo, teamId = activeTeamId) },
        ) { padding ->
            Surface( // A surface container using the 'background' color from the theme
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding), color = MaterialTheme.colorScheme.background
            ) {
                NavHost(navController = navController, startDestination = "/home") {
                    composable(route = "/home") {
                        navigateTo("/${activeTeamId.ifBlank { "no_team" }}/${Route.TeamTasks.name}")
                    }

                    composable(
                        route = "/{teamId}/${Route.TeamTasks.name}",
                        arguments = listOf(navArgument("teamId") { type = NavType.StringType; nullable = false; defaultValue = "" })
                    ) {

                        vm.changeActiveTeamId(it.arguments?.getString("teamId") ?: "")
                        vm.setActivePage(Route.TeamTasks.title)
                        if (it.arguments?.getString("teamId") == "no_team" || it.arguments?.getString("teamId") == null || it.arguments?.getString("teamId") == "") {
                            navigateTo("/no_team/${Route.TeamTasks.name}")
                        }
                        TeamTaskScreenWrapper(onItemSelect = onItemSelect)
                    }

                    composable(route = Route.MyTasks.name) {
                        vm.setActivePage(Route.MyTasks.title)
                        taskVM.ActiveTask.value = ""
                        PersonalTasksScreenWrapper(onItemSelect = onItemSelect, activeUser = app.user.value.email)
                    }

                    composable(route = Route.ChatScreen.name) {
                        taskVM.ActiveTask.value = ""
                        vm.setActivePage(Route.ChatScreen.title)
                        ChatList(onChatClick = onItemSelect)
                    }

                    composable(
                        route = "${Route.ChatScreen.name}/{index}",
                        arguments = listOf(
                            navArgument("index") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        val userId = entry.arguments?.getString("index")
                        val destUser = userId?.let { teamMembers.find { it.email == userId } }

                        Log.d("chat", "Trying to access chat with $userId")
                        Log.d("chat", "We're on team $activeTeamId")
                        Log.d("chat", teamMembers.toString())

                        userVM.setCurrDestUser(destUser?.email?:"")
                        taskVM.ActiveTask.value = ""
                        if (destUser != null) {
                            vm.setActivePage(Route.ChatScreen.title + "/" + "${destUser.firstName} ${destUser.lastName}")
                            Chat(destUser.email)
                        }
                    }

                    composable(route = "${Route.ChatScreen.name}/group") {
                        vm.setActivePage(Route.ChatScreen.title + "/" + activeTeam.name)
                        taskVM.ActiveTask.value = ""
                        GroupChat()
                    }

                    composable(route = Route.NewChat.name) {
                        vm.setActivePage(Route.NewChat.title)
                        taskVM.ActiveTask.value = ""
                        NewChat(onChatClick = onItemSelect)
                    }

                    composable(route = Route.TeamScreen.name) {
                        vm.setActivePage(Route.TeamScreen.title)
                        taskVM.ActiveTask.value = ""
                        TeamScreen(
                            onTaskClick = onItemSelect,
                            removeTeam = vm.removeTeam,
                            leaveTeam = vm.leaveTeam,
                            context = LocalContext.current,
                            navigateTo = navigateTo,
                            user = vm.user
                        )
                    }

                    composable(route = Route.NewTask.name) {
                        vm.setActivePage(Route.NewTask.title)
                        taskVM.ActiveTask.value = ""
                        if (taskVM.task.value.title != "New Task")
                            taskVM.setTask(Task(title = "New Task", section = sections[0]))
                        NewTaskScreen(changeRoute = onItemSelect, vm = taskVM)//app
                    }

                    composable(
                        route = "${Route.TeamTasks.name}/{index}",
                        arguments = listOf(
                            navArgument("index") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        val taskId = entry.arguments?.getString("index")
                        tasksList.value.find { it.id == taskId }?.let {
                            Log.d("TITLE", it.toString() )
                            vm.setActivePage(it.title)
                            taskVM.ActiveTask.value = taskId.orEmpty()

                            ShowTaskDetails(it, user, onComplete = { task ->
                                val beforeUpdate = task.copy()
                                task.complete()
                                taskVM.updateTaskHistory(beforeUpdate, task)
                                taskVM.onTaskUpdated(task)
                                onItemSelect(1, null, null, null, null)
                            })
                        }
                    }

                    composable(
                        route = "${Route.EditTask.name}/{index}",
                        arguments = listOf(
                            navArgument("index") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        val index = entry.arguments?.getString("index")
                        val taskEditing = tasksList.value.find { it.id == index }
                        taskVM.ActiveTask.value = ""

                        tasksList.value.find { it.id == index }?.let {
                            vm.setActivePage(it.title)
                            if (taskVM.task.value.id != it.id)
                                taskVM.setTask(it)
                        }

                        if (taskEditing != null) {
                            EditTaskScreen(changeRoute = onItemSelect, taskVM = taskVM)
                        }
                    }

                    composable(
                        route = "${Route.UserView.name}/{index}",
                        arguments = listOf(
                            navArgument("index") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        vm.setActivePage(Route.UserView.title)
                        taskVM.ActiveTask.value = ""
                        val userId = entry.arguments?.getString("index")
                        var user = User()
                        if (userId != null) {
                            user = vm.fetchUsers(activeTeamId).collectAsState(initial = emptyList()).value.find { it.email == userId } ?: User()
                        }
                        UserScreen(user = user, personalInfo = false, onLogout = onLogout)
                    }

                    composable(route = Route.UserView.name) {
                        taskVM.ActiveTask.value = ""
                        vm.setActivePage(Route.UserView.title)
                        UserScreen(user = app.user.value, personalInfo = true, onLogout = onLogout)
                    }

                    composable(
                        "deeplink",
                        deepLinks = listOf(navDeepLink { uriPattern = "https://www.workstream.it/{teamId}"; action = Intent.ACTION_VIEW }),
                        arguments = listOf(
                            navArgument("teamId") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        taskVM.ActiveTask.value = ""
                        Log.d("confirm_join_team", "YOO")
                        val teamId = entry.arguments?.getString("teamId") ?: ""
                        ConfirmJoinTeamPage(
                            teamId = teamId,
                            onConfirm = {
                                vm.joinTeam(teamId, user.email)
                                navController.navigate("/${teamId}/${Route.TeamTasks.name}")
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                    composable(
                        "profile?id={teamId}",
                        deepLinks = listOf(navDeepLink { uriPattern = "https://www.workstream.it/{teamId}"; action = Intent.ACTION_VIEW }),
                        arguments = listOf(
                            navArgument("teamId") {
                                type = NavType.StringType
                                nullable = false
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        taskVM.ActiveTask.value = ""
                        val teamId = entry.arguments?.getString("teamId") ?: ""
                        ConfirmJoinTeamPage(
                            teamId = teamId,
                            onConfirm = {
                                vm.joinTeam(teamId, user.email)
                                navController.navigate("/${teamId}/${Route.TeamTasks.name}")
                            },
                            onCancel = { navController.popBackStack() },
                        )
                    }

                    composable(
                        "/no_team/${Route.TeamTasks.name}"
                    ) {
                        taskVM.ActiveTask.value = ""
                        vm.setActivePage("no_team")
                        NoTeamsScreen(activeUser = app.user, onJoinTeam = { /* no action needed */ }, addNewTeam = app::createEmptyTeam, navigateToTeam = { navigateTo("/$it/${Route.TeamTasks.name}") }, logout = onLogout)
                    }
                    composable(
                        "//${Route.TeamTasks.name}"
                    ) {
                        taskVM.ActiveTask.value = ""
                        vm.setActivePage("no_team")
                        NoTeamsScreen(activeUser = app.user, onJoinTeam = { /* no action needed */ }, addNewTeam = app::createEmptyTeam, navigateToTeam = { navigateTo("/$it/${Route.TeamTasks.name}") }, logout = onLogout)
                    }
                }
            }
        }
    }
}

enum class Route(val title: String) {
    Back(title = "back"),
    MyTasks(title = "My Tasks"),
    TeamTasks(title = "Team Tasks"),
    TeamMembers(title = "Team Members"),
    NewTask(title = "New task"),
    EditTask(title = "Edit task"),
    TeamScreen(title = "Team Members"),
    UserView(title = "User View"),
    ChatScreen(title = "Chat Screen"),
    NewChat(title = "New Chat"),
}
