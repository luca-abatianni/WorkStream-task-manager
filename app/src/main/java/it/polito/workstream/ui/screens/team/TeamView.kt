package it.polito.workstream.ui.screens.team

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import it.polito.workstream.Route
import it.polito.workstream.ui.models.Team
import it.polito.workstream.ui.models.User
import it.polito.workstream.ui.shared.ProfilePicture
import it.polito.workstream.ui.theme.isLight
import it.polito.workstream.ui.viewmodels.TaskListViewModel
import it.polito.workstream.ui.viewmodels.TeamViewModel
import it.polito.workstream.ui.viewmodels.ViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun TeamScreen(
    vm: TeamViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    onTaskClick: (route: Int, taskId: String?, taskName: String?, userId: Long?, userMail: String?) -> Unit,
    removeTeam: (teamId: String, team: Team) -> Unit,
    leaveTeam: (teamId: String, userId: String) -> Unit,
    context: Context,
    navigateTo: (route: String) -> Any,
    tasksVm: TaskListViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
    user: StateFlow<User>,
) {

    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmationDialog by remember { mutableStateOf(false) }
    val userProfile = user.collectAsState(initial = null).value
    val activeTeamId by vm.activeTeamId.collectAsState(initial = "no_team")
    val team = vm.fetchTeam(activeTeamId).collectAsState(initial = null).value ?: Team(name = "Loading...")
    val teamMembers = vm.teamMembers.collectAsState(initial = emptyList()).value
    val teamTasks = tasksVm.tasks.collectAsState(initial = emptyList()).value

    val photoState = remember { mutableStateOf(team.photo) }
    photoState.value = team.photo

    var nameValue by remember { mutableStateOf(team.name) }
    nameValue = team.name
    val numberOfMembers = teamMembers.size  // Number of members
    val tasksCompleted = teamTasks.filter { it.completed }.size    // Total number of tasks completed
    val tasksToComplete = teamTasks.size - tasksCompleted // Number of tasks to complete
    val top3Users = (teamMembers.sortedByDescending { teamTasks.filter { task -> task.assignee == it.email && task.completed }.size }.take(3))
    val isHorizontal = LocalContext.current.resources.configuration.screenWidthDp > LocalContext.current.resources.configuration.screenHeightDp

    val link = "https://www.workstream.it/${team.id}"
    val scope = rememberCoroutineScope()

    if (showDialog) {
        InviteMemberDialog(onDismiss = { showDialog = false }, qrCodeBitmap = getQrCodeBitmap(link), link = link, context = context)
    }

    if (showDeleteConfirmationDialog) {
        DeleteTeamConfirmationDialog(
            onDismiss = { showDeleteConfirmationDialog = false },
            onConfirm = {
                removeTeam(team.id, team)

                userProfile?.teams?.firstOrNull { it != team.id }
                    .let { vm.changeActiveTeamId(it ?: "no_team") }

                showDeleteConfirmationDialog = false
                onTaskClick(1, null, null, null, null)
                navigateTo(Route.TeamScreen.name)
            }
        )
    }

    if (showLeaveConfirmationDialog) {
        LeaveTeamConfirmationDialog(
            onDismiss = { showLeaveConfirmationDialog = false },
            onConfirm = {
                leaveTeam(team.id, vm.currentUser.email)
                showLeaveConfirmationDialog = false
                navigateTo(Route.TeamScreen.name)
            }
        )
    }

    if (isHorizontal) {
        // Floating buttons
        Row(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .padding(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.End
        ) {
            if (vm.currentUser.email == team.admin) {
                OutlinedButton(onClick = { showDeleteConfirmationDialog = true }, colors = ButtonDefaults.outlinedButtonColors().copy(contentColor = MaterialTheme.colorScheme.error, containerColor = MaterialTheme.colorScheme.background)) {
                    Text("Delete Team")
                    Icon(
                        Icons.Default.DeleteOutline, contentDescription = "Delete Team", modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(20.dp)
                    )
                }
            } else {
                OutlinedButton(onClick = { showLeaveConfirmationDialog = true }, colors = ButtonDefaults.outlinedButtonColors().copy(contentColor = MaterialTheme.colorScheme.error, containerColor = MaterialTheme.colorScheme.background)) {
                    Text("Leave team")
                    Icon(
                        Icons.Default.DeleteOutline, contentDescription = "Leave Team", modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = { showDialog = true }) {
                Text("Invite Member")
                Icon(
                    Icons.Default.PersonAdd, contentDescription = "Save changes", modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfilePicture(
                        basepath = team.id + userProfile?.email,
                        photo = photoState,
                        profilePicture = team.profilePicture,
                        photoBitmapValue = team.profileBitmap,
                        isEditing = (vm.currentUser.email == team.admin),
                        name = team.name,
                        edit = {
                            scope.launch {

                                team.photo = "LocalImage"
                                vm.uploadPhoto(team)
                            }
                        },
                        setPhotoBitmap = {
                            scope.launch {

                                team.photo = "LocalImage"
                                vm.uploadPhoto(team)
                            }
                        },
                        setPhoto = {
                            team.photo = it
                            vm.uploadPhoto(team)
                        }

                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = team.name, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        if (vm.currentUser.email == team.admin) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit team name",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(20.dp)
                                    .clickable { vm.edit(nameValue) }
                            )
                        }

                        if (vm.showEditDialog) {
                            AlertDialog(onDismissRequest = vm::discard,
                                title = { Text("Edit Team name") },
                                text = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        OutlinedTextField(
                                            value = nameValue,
                                            onValueChange = { nameValue = it },
                                            label = { Text("Team Name") },
                                            isError = vm.nameError.isNotBlank(),
                                            leadingIcon = { Icon(Icons.Default.PeopleAlt, contentDescription = "Team Name") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (vm.nameError.isNotBlank()) { // Small text with error
                                            Text(text = vm.nameError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { vm.save(nameValue, team) }) { Text("Save") } },//QUI
                                dismissButton = { TextButton(onClick = vm::discard) { Text("Cancel") } }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // KPI: number of tasks completed, number of tasks to complete
                // Progress bar of tasks completed vs tasks to complete
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (tasksToComplete == 0 && tasksCompleted == 0) Text("No tasks assigned yet!", style = MaterialTheme.typography.bodyLarge)
                    else {
                        val progress = if (tasksToComplete > 0) tasksCompleted.toFloat() / (tasksToComplete + tasksCompleted) else 1f
                        val progressPercentage = (progress * 100).toInt()
                        if (progressPercentage == 100) Text("All tasks completed!", style = MaterialTheme.typography.bodyLarge)
                        else Text(text = "Tasks completed: ${tasksCompleted}/${tasksToComplete + tasksCompleted} ($progressPercentage%)", style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .size(6.dp),
                            color = Color(0xFF43A047),
                            trackColor = Color(0xFFE53935),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                // KPI: best 3 users by tasks completed
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Top 3 members by completed tasks", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    top3Users.forEachIndexed { index, userId ->
                        val user = vm.teamMembers.collectAsState(initial = emptyList()).value.find { it.email == userId.email }
                        val numOfTasksCompleted = teamTasks.filter { it.completed && (it.assignee == userId.email) }.size

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                Text(
                                    "${index + 1}", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier
                                        .background(
                                            color = (if (index == 0) Color(0xFFFFD700) else if (index == 1) Color(
                                                0xFFC0C0C0
                                            ) else Color(0xFFCD7F32)),
                                            shape = CircleShape
                                        )
                                        .size(30.dp)
                                        .padding(top = 1.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(text = user?.getFirstAndLastName() ?: "", fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check, contentDescription = "Tasks completed", modifier = Modifier
                                        .padding(top = 3.dp, end = 3.dp)
                                        .size(16.dp)
                                )
                                Text(
                                    text = "$numOfTasksCompleted tasks",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Team members
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Manage members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                MemberList(
                    members = vm.teamMembers.collectAsState(initial = emptyList()).value,
                    removeMember = leaveTeam,
                    currentUser = vm.currentUser,
                    adminId = team.admin,
                    teamId = team.id,
                    navigateTo = navigateTo
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (!isHorizontal) {
            if (vm.currentUser.email == team.admin) {
                OutlinedButton(
                    onClick = { showDeleteConfirmationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                        .copy(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete Team")
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Team",
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(20.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { showLeaveConfirmationDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors().copy(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Leave team")
                    Icon(
                        Icons.Default.DeleteOutline, contentDescription = "Leave Team", modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(20.dp)
                    )
                }
            }
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Invite Member")
                Icon(
                    Icons.Default.PersonAdd, contentDescription = "Save changes", modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                )
            }
        }
    }
}


@Composable
fun MemberList(
    members: List<User>,
    removeMember: (teamId: String, userId: String) -> Unit,
    currentUser: User,
    adminId: String,
    teamId: String,
    navigateTo: (route: String) -> Any,
) {
    Column {
        members.forEach { member ->
            MemberItem(
                member = member,
                removeMember = removeMember,
                currentUser = currentUser,
                isAdmin = member.email == adminId,
                adminId = adminId,
                teamId = teamId,
                navigateTo = navigateTo
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun MemberItem(
    member: User,
    removeMember: (teamId: String, userId: String) -> Unit,
    currentUser: User,
    isAdmin: Boolean,
    adminId: String?,
    teamId: String,
    navigateTo: (route: String) -> Any,
) {
    var removeMemberConfirmationDialog by remember { mutableStateOf(false) }

    if (removeMemberConfirmationDialog) {
        RemoveMemberConfirmationDialog(
            onDismiss = { removeMemberConfirmationDialog = false },
            onConfirm = {
                removeMember(teamId, member.email)
                removeMemberConfirmationDialog = false
                navigateTo(Route.TeamScreen.name)
            },
            memberName = "${member.firstName} ${member.lastName}"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navigateTo("${Route.UserView.name}/${member.email}")
            }
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp)
    ) {
        if (member.photo.isNotEmpty()) {
            AsyncImage(
                model =
                ImageRequest.Builder(LocalContext.current)
                    .data(LocalContext.current.getFileStreamPath(member.photo).absolutePath)
                    .crossfade(true)
                    .build(), //minchia ci siamo
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.getFirstAndLastName().trim().split(" ").map { it.first().uppercaseChar() }.joinToString("").take(2),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${member.firstName} ${member.lastName}",
                    fontWeight = if (isAdmin) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 17.sp
                )
                if (isAdmin || currentUser.email == member.email) {
                    Text(
                        text = buildString {
                            if (isAdmin) append(" (Admin)")
                            if (currentUser.email == member.email) append(" (You)")
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Text(text = member.email, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            if (!member.location.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp)
                    )
                    Text(text = member.location!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (currentUser.email != member.email && currentUser.email == adminId) {
            IconButton(onClick = { removeMemberConfirmationDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove member")
            }
        }
    }
}

@Composable
fun DeleteTeamConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Team") },
        text = { Text("Are you sure you want to delete this team? This action cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RemoveMemberConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, memberName: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Remove Member") },
        text = { Text(text = "Are you sure you want to remove $memberName from the team? This action cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
fun LeaveTeamConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Leave Team") },
        text = { Text("Are you sure you want to leave this team? This action cannot be undone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Leave") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun getQrCodeBitmap(content: String): Bitmap {
    val size = 512 //pixels
    val hints = hashMapOf<EncodeHintType, Int>().also { it[EncodeHintType.MARGIN] = 1 } // Make the QR code buffer border narrower
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also {
        for (x in 0 until size) {
            for (y in 0 until size) {
                it.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}

@Composable
fun InviteMemberDialog(onDismiss: () -> Unit, qrCodeBitmap: Bitmap, link: String, context: Context) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Invite Member") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.verticalScroll(rememberScrollState())) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        //.fillMaxSize()
                        .padding(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = link,
                            color = if(MaterialTheme.colorScheme.isLight()) Color.Blue else Color(0xFF2196F3), // Light blue
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { context.shareLink(link) }
                        )
                    }
                    Column(modifier = Modifier.weight(0.1f)) {
                        Icon(Icons.Default.Share, contentDescription = "Share link", modifier = Modifier
                            .size(24.dp)
                            .clickable { context.shareLink(link) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

fun Context.shareLink(url: String) {
    val sendIntent = Intent(
        Intent.ACTION_SEND
    ).apply {
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    startActivity(shareIntent)
}
