package com.multitetris.network.social

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.multiTetrisSocialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "multitetris_social_prefs"
)

data class LocalSocialSnapshot(
    val profile: UserProfile,
    val friends: List<SavedFriend>
)

/**
 * Persists the user's permanent Friend Code, editable nickname, skin, and saved Friends List
 * locally using Jetpack DataStore.
 */
class LocalProfileDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val snapshotFlow: Flow<LocalSocialSnapshot> = context.multiTetrisSocialDataStore.data.map { prefs ->
        val code = prefs[KEY_FRIEND_CODE].orEmpty()
        val nickname = prefs[KEY_NICKNAME].orEmpty()
        val uid = prefs[KEY_UID].orEmpty()
        val skinId = prefs[KEY_SELECTED_SKIN] ?: "classic"
        val friends = decodeFriends(prefs[KEY_SAVED_FRIENDS_JSON])
        LocalSocialSnapshot(
            profile = UserProfile(
                uid = uid,
                friendCode = code,
                nickname = nickname,
                selectedSkinId = skinId
            ),
            friends = friends
        )
    }

    suspend fun ensureInitializedProfile(defaultSkinId: String = "classic"): UserProfile {
        var result = UserProfile()
        context.multiTetrisSocialDataStore.edit { prefs ->
            val existingCode = prefs[KEY_FRIEND_CODE]?.takeIf { it.isNotBlank() }
            val code = existingCode ?: FriendCodeGenerator.generate().also {
                prefs[KEY_FRIEND_CODE] = it
            }
            val nickname = prefs[KEY_NICKNAME]?.takeIf { it.isNotBlank() }
                ?: FriendCodeGenerator.defaultNicknameFor(code).also {
                    prefs[KEY_NICKNAME] = it
                }
            val skinId = prefs[KEY_SELECTED_SKIN] ?: defaultSkinId.also {
                prefs[KEY_SELECTED_SKIN] = it
            }
            val uid = prefs[KEY_UID].orEmpty()
            result = UserProfile(
                uid = uid,
                friendCode = code,
                nickname = nickname,
                selectedSkinId = skinId
            )
        }
        return result
    }

    suspend fun getSnapshot(): LocalSocialSnapshot = snapshotFlow.first()

    suspend fun saveUid(uid: String) {
        context.multiTetrisSocialDataStore.edit { prefs ->
            prefs[KEY_UID] = uid
        }
    }

    suspend fun saveFriendCode(friendCode: String) {
        context.multiTetrisSocialDataStore.edit { prefs ->
            prefs[KEY_FRIEND_CODE] = friendCode
        }
    }

    suspend fun saveNickname(nickname: String) {
        val sanitized = nickname.trim().take(20)
        if (sanitized.isEmpty()) return
        context.multiTetrisSocialDataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = sanitized
        }
    }

    suspend fun saveSelectedSkin(skinId: String) {
        context.multiTetrisSocialDataStore.edit { prefs ->
            prefs[KEY_SELECTED_SKIN] = skinId
        }
    }

    suspend fun addOrUpdateFriend(friend: SavedFriend) {
        context.multiTetrisSocialDataStore.edit { prefs ->
            val current = decodeFriends(prefs[KEY_SAVED_FRIENDS_JSON]).toMutableList()
            val existingIndex = current.indexOfFirst { it.uid == friend.uid }
            if (existingIndex >= 0) {
                current[existingIndex] = friend
            } else {
                current.add(friend)
            }
            prefs[KEY_SAVED_FRIENDS_JSON] = json.encodeToString(current)
        }
    }

    suspend fun removeFriend(friendUid: String) {
        context.multiTetrisSocialDataStore.edit { prefs ->
            val filtered = decodeFriends(prefs[KEY_SAVED_FRIENDS_JSON])
                .filterNot { it.uid == friendUid }
            prefs[KEY_SAVED_FRIENDS_JSON] = json.encodeToString(filtered)
        }
    }

    private fun decodeFriends(raw: String?): List<SavedFriend> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedFriend>>(raw) }
            .getOrDefault(emptyList())
    }

    companion object {
        private val KEY_UID = stringPreferencesKey("uid")
        private val KEY_FRIEND_CODE = stringPreferencesKey("friend_code")
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_SELECTED_SKIN = stringPreferencesKey("selected_skin_id")
        private val KEY_SAVED_FRIENDS_JSON = stringPreferencesKey("saved_friends_json")
    }
}
