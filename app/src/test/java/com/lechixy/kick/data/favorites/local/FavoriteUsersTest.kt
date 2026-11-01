package com.lechixy.kick.data.favorites.local

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FavoriteUsersTest {
    @Test fun knownUserKeepsFavoriteAfterRenameButDoesNotMatchAReassignedName() {
        val favorites = FavoriteUsers().toggle(FavoriteUser(42, "Tester", "tester"))
        assertTrue(favorites.contains(42, "Renamed", "renamed"))
        assertFalse(favorites.contains(99, "Tester", "tester"))
        assertTrue(favorites.contains(null, "TESTER"))
    }

    @Test fun profileWithoutIdMatchesLaterChatMessagesWithId() {
        val favorites = FavoriteUsers().toggle(FavoriteUser(username = "İnci", slug = "inci"))
        assertTrue(favorites.contains(42, "İnci", "inci"))
        assertTrue(favorites.contains(null, "other", "INCI"))
        assertFalse(favorites.contains(null, ""))
        assertTrue(favorites.toggle(FavoriteUser(42, "İnci", "inci")).users.isEmpty())
    }

    @Test fun favoritesSurviveTheStoredJsonRoundTrip() {
        val favorites = FavoriteUsers().toggle(FavoriteUser(42, "Tester", "tester"))
            .toggle(FavoriteUser(99, "Other", "other"))
        val stored = Json.encodeToString(favorites.users)
        val restored = FavoriteUsers(Json.decodeFromString<List<FavoriteUser>>(stored))
        assertTrue(restored.contains(42, "Tester"))
        assertTrue(restored.contains(99, "Other"))
        val removed = restored.toggle(FavoriteUser(42, "ChangedName", "changedname"))
        assertFalse(removed.contains(42, "Tester"))
        assertTrue(removed.contains(99, "Other"))
    }

    @Test fun blankIdentitiesCannotBeFavorited() {
        assertTrue(FavoriteUsers().toggle(FavoriteUser(username = " ", slug = "")).users.isEmpty())
    }
}
