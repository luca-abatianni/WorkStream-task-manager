package it.polito.workstream.ui.Login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import it.polito.workstream.MainActivity
import it.polito.workstream.MainApplication
import it.polito.workstream.R
import it.polito.workstream.ui.models.User
import it.polito.workstream.ui.theme.WorkStreamTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : ComponentActivity() {
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WorkStreamTheme {
                var user by remember { mutableStateOf(Firebase.auth.currentUser) }
                val token = stringResource(R.string.web_client_id)
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)!!
                        val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                        scope.launch {
                            val authResult = Firebase.auth.signInWithCredential(credential).await()
                            val isLogin = authResult.additionalUserInfo?.isNewUser == false
                            onAuthComplete(authResult, isLogin)
                        }
                    } catch (e: ApiException) {
                        onAuthError(e)
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                    ) {
                        if (user == null) {
                            Text("Not logged in")
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(token)
                                    .requestEmail()
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                launcher.launch(googleSignInClient.signInIntent)
                            }) {
                                Text("Sign in via Google")
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(user!!.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Welcome ${user!!.displayName}")
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = {
                                Firebase.auth.signOut()
                                user = null
                            }) {
                                Text("Sign out")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onAuthComplete(authResult: AuthResult, isLogin: Boolean) {
        val user = authResult.user
        if (user != null) {
            if (isLogin) {
                fetchUserFromFirestore(user)
            } else {
                registerUserInFirestore(user)
            }
        }
    }

    private fun onAuthError(e: ApiException) {
        Log.e("ERROR", "Error during authentication", e)
        // Handle error (e.g., show a message to the user)
    }

    private fun fetchUserFromFirestore(firebaseUser: FirebaseUser) {
        val userRef = db.collection("users").document(firebaseUser.email!!)
        userRef.get().addOnSuccessListener { document ->
            val app = applicationContext as MainApplication
            if (document.exists()) {
                // Ottieni i dati dal documento
                val user = document.toObject(User::class.java)!!
                user.profilePicture = firebaseUser.photoUrl.toString()

                // Set the user in the MainApplication instance
                app._user.value = user
                navigateToMainActivity()
            } else {
                registerUserInFirestore(firebaseUser)
            }
        }.addOnFailureListener { e ->
            Log.e("ERROR", "Error fetching user document", e)
            Firebase.auth.signOut()
            // Optionally show an error message to the user
        }
    }

    private fun registerUserInFirestore(firebaseUser: FirebaseUser) {
        val userRef = db.collection("users").document(firebaseUser.email!!)
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                Log.d("ERROR", "User already exists, cannot sign up")
                Firebase.auth.signOut()
                // Optionally show an error message to the user
            } else {
                val names = firebaseUser.displayName?.split(" ") ?: listOf("")
                val firstName = names.firstOrNull() ?: ""
                val lastName = names.drop(1).joinToString(" ")

                val newUser = User(
                    id = User.getNewId(),
                    firstName = firstName,
                    lastName = lastName,
                    email = firebaseUser.email ?: "",
                    location = "",
                    profilePicture = firebaseUser.photoUrl.toString()
                )
                userRef.set(newUser).addOnSuccessListener {
                    val app = applicationContext as MainApplication
                    app._user.value = newUser
                    navigateToMainActivity()
                }.addOnFailureListener { e ->
                    Log.e("ERROR", "Error saving new user", e)
                    Firebase.auth.signOut()
                    // Optionally show an error message to the user
                }
            }
        }.addOnFailureListener { e ->
            Log.e("ERROR", "Error checking user existence", e)
            Firebase.auth.signOut()
            // Optionally show an error message to the user
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
