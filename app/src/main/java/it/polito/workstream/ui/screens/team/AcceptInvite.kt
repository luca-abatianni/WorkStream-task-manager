package it.polito.workstream.ui.screens.team


import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.polito.workstream.ui.models.Team
import it.polito.workstream.ui.shared.ProfilePicture
import it.polito.workstream.ui.viewmodels.TeamListViewModel
import it.polito.workstream.ui.viewmodels.ViewModelFactory

@Composable
fun ConfirmJoinTeamPage(
    teamId: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    vm: TeamListViewModel = viewModel(factory = ViewModelFactory(LocalContext.current)),
) {
    val team = vm.fetchTeam(teamId).collectAsState(initial = null).value
    val members = vm.fetchUsers(teamId).collectAsState(initial = emptyList()).value
    val photoState = remember {mutableStateOf(team?.photo ?: "")}
    photoState.value = team?.photo ?: ""
    val user by vm.user.collectAsState()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (team != null) {
            ProfilePicture(profilePicture = team.profilePicture, photoBitmapValue = team.profileBitmap, setPhotoBitmap = {}, name = team.name, isEditing = false, setPhoto = {}, photo = photoState )
            Spacer(modifier = Modifier.height(8.dp))
            Text(team.name, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(text = "Team Members", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(50.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Profile picture
                            if(member.photo.isNotEmpty()){
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
                            } else{ Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${member.firstName} ${member.lastName}".trim().split(" ").map { it.first().uppercaseChar() }.joinToString("").take(2),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    lineHeight = 12.sp * 1.25f
                                )
                            }
                            }
                            // Member name
                            Text(text = "${member.firstName} ${member.lastName}")
                        }
                    }
                }
            }
            if (team.members.contains(user.email))
                Text(text = "You are already a member of this team", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {

                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { onCancel() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                        Icon(
                            Icons.Outlined.Cancel, contentDescription = "Cancel", modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Button(onClick = { onConfirm() }, modifier = Modifier.fillMaxWidth(),
                        enabled = !team.members.contains(user.email)) {
                        Text("Join Team")
                        Icon(
                            Icons.Outlined.AddReaction, contentDescription = "Join team", modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp)
                        )
                    }
                }
            }
        } else {
            Icon(Icons.Filled.ErrorOutline, contentDescription = "Team not found", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Team not found", style = MaterialTheme.typography.displaySmall)
        }
    }
}
