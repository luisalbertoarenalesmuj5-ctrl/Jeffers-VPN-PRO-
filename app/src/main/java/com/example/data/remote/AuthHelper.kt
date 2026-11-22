package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseApp not initialized in this environment", t)
            false
        }
    }

    fun authStateFlow(context: Context): Flow<FirebaseUser?> {
        if (!isFirebaseAvailable(context)) {
            return flowOf(null)
        }
        return callbackFlow {
            val auth = Firebase.auth
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser)
            }
            auth.addAuthStateListener(listener)
            trySend(auth.currentUser)
            awaitClose { auth.removeAuthStateListener(listener) }
        }
    }

    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (!isFirebaseAvailable(context)) return false
        val serverClientId = resolveWebClientId(context) ?: return false
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            Firebase.auth.signInWithCredential(firebaseCred).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credentials cached for silent sign-in: ${e.message}")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Silent Google sign-in cancelled")
            false
        } catch (e: GetCredentialException) {
            Log.d(TAG, "Silent Google sign-in skipped: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error during silent Google sign-in", e)
            false
        }
    }

    suspend fun signInWithGoogle(context: Context): GoogleSignInOutcome {
        if (!isFirebaseAvailable(context)) {
            return GoogleSignInOutcome.Error("Firebase no está inicializado en este dispositivo.")
        }
        val serverClientId = resolveWebClientId(context)
            ?: return GoogleSignInOutcome.Error("Falta configuración OAuth (default_web_client_id).")

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                val user = authResult.user
                if (user != null) {
                    GoogleSignInOutcome.Success(user)
                } else {
                    GoogleSignInOutcome.Error("No se pudo obtener el usuario de Google.")
                }
            } else {
                GoogleSignInOutcome.Error("Credencial de Google no compatible.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available on device", e)
            GoogleSignInOutcome.Error(
                "No se encontró una cuenta de Google disponible en el dispositivo. Agrega una cuenta en Ajustes."
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            GoogleSignInOutcome.Error(
                "No se pudo iniciar sesión con Google (${e.message ?: "error de credenciales"})."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Auth failed", e)
            GoogleSignInOutcome.Error(
                "Error al autenticar con Google: ${e.message ?: "intenta de nuevo"}"
            )
        }
    }

    suspend fun signOut(context: Context) = withContext(NonCancellable) {
        if (!isFirebaseAvailable(context)) return@withContext
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed", e)
        }
        try {
            Firebase.auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase signOut failed", e)
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            val resId = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (resId == 0) null else context.getString(resId).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
