package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

data class FirestoreErrorInfo(
    val error: String,
    val operationType: String,
    val path: String?,
    val uid: String?,
    val email: String?
)

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Exception {
    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }
    val json = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path ?: JSONObject.NULL)
        put("uid", currentUser?.uid ?: JSONObject.NULL)
        put("email", currentUser?.email ?: JSONObject.NULL)
    }.toString()
    Log.e("FirestoreError", "Firestore Error: $json", error)
    return Exception(json, error)
}
