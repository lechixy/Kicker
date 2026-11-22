package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.StreamHostEvent
import com.lechixy.kick.data.chat.model.StreamHostedEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentStreamHostsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val nested = json.decodeFromString<StreamHostedEvent>(
        """{"message":{"id":"1edc5ac9-006c-4ab4-b968-b9e19cd4769a","numberOfViewers":8,"optionalMessage":null,"createdAt":"2026-11-22T15:46:39.273397Z"},"user":{"id":29282340,"username":"SpaceEscape","isSuperAdmin":false,"verified":false}}"""
    )
    private val flat = json.decodeFromString<StreamHostEvent>(
        """{"chatroom_id":25951243,"optional_message":null,"number_viewers":8,"host_username":"SpaceEscape"}"""
    )
    private val normalized = flat.toStreamHostedEvent()

    @Test fun flatPayloadNormalizesToTheExistingHostEvent() {
        assertEquals(25951243L, flat.chatroomId)
        assertEquals(nested.user.username, normalized.user.username)
        assertEquals(nested.message.numberOfViewers, normalized.message.numberOfViewers)
        assertNull(normalized.message.id)
        assertNull(normalized.user.id)
    }

    @Test fun acceptsOnlyOneOfTheTwoFormatsInEitherOrder() {
        for ((first, second) in listOf(nested to normalized, normalized to nested)) {
            val hosts = RecentStreamHosts()
            assertTrue(hosts.accept(first, now = 0))
            assertFalse(hosts.accept(second, now = 100))
            assertFalse(hosts.accept(nested, now = 200))
        }
    }

    @Test fun separateHostsWithIdenticalDisplayTextEachGetOneNotification() {
        val hosts = RecentStreamHosts()
        val anotherHost = nested.copy(message = nested.message.copy(id = "another-host"))
        assertTrue(hosts.accept(nested, now = 0))
        assertTrue(hosts.accept(anotherHost, now = 100))
        assertFalse(hosts.accept(normalized, now = 200))
        assertFalse(hosts.accept(normalized, now = 300))
        assertTrue(hosts.accept(normalized, now = 400))
    }

    @Test fun matchingRequiresTheSameContentWithinFiveSeconds() {
        val hosts = RecentStreamHosts()
        assertTrue(hosts.accept(nested, now = 0))
        assertTrue(hosts.accept(flat.copy(numberOfViewers = 9).toStreamHostedEvent(), now = 100))
        assertTrue(hosts.accept(flat.copy(optionalMessage = "Selam!").toStreamHostedEvent(), now = 200))
        assertTrue(hosts.accept(normalized, now = 5_001))
    }

    @Test fun clearingRemovesThePreviousRoomsDeliveries() {
        val hosts = RecentStreamHosts()
        assertTrue(hosts.accept(nested, now = 0))
        hosts.clear()
        assertTrue(hosts.accept(normalized, now = 100))
    }
}
