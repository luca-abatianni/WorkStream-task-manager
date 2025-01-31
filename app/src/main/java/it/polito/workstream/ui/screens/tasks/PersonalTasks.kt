package it.polito.workstream.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.polito.workstream.ui.models.Task
import it.polito.workstream.ui.screens.tasks.components.SmallTaskBox
import it.polito.workstream.ui.theme.WorkStreamTheme
import it.polito.workstream.ui.viewmodels.TaskListViewModel
import it.polito.workstream.ui.viewmodels.ViewModelFactory

@Composable
fun PersonalTasksScreen(
    getOfUser: (String, List<Task>, String) -> List<Task>,
    onTaskClick: (route: Int, taskId: String?, taskName: String?, userId: Long?, userMail: String?) -> Unit,
    activeUser: String,
    _tasksList: State<List<Task>>,
    currentSortOrder: State<String>,
    vm: TaskListViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))
) {
    val tasksList = vm.tasks.collectAsState(initial = emptyList()).value
    val sortOrder = currentSortOrder.value
    val sortedTasks = getOfUser(activeUser, tasksList, sortOrder)

    WorkStreamTheme {
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("Add new task") },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Task") },
                    onClick = { onTaskClick(3, null, null, null, null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp)
                        .height(40.dp)
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            content = { padding ->
                LazyColumn(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(sortedTasks) { task ->
                        Column(
                            modifier = Modifier.clickable { onTaskClick(1, task.id, task.title, null, null) }
                        ) {
                            SmallTaskBox(
                                title = task.title,
                                section = task.section,
                                assignee = null,
                                dueDate = task.dueDate,
                                task = task,
                                onEditClick = {
                                    onTaskClick(4, task.id, task.title, null, null)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun PersonalTasksScreenWrapper(
    vm: TaskListViewModel = viewModel(
        factory = ViewModelFactory(
            LocalContext.current
        )
    ),
    onItemSelect: (route: Int, taskId: String?, taskName: String?, userId: Long?, userMail: String?) -> Unit,
    activeUser: String
) {
    val tasksList = vm.tasks.collectAsState(initial = emptyList())
    val currentSortOrder = vm.currentSortOrder.collectAsState()
    PersonalTasksScreen(getOfUser = vm::getOfUser, onItemSelect, activeUser, tasksList, currentSortOrder)
}
