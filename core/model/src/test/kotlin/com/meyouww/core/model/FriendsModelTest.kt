package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.Instant

class FriendsModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `levels know what they can move to`() {
        assertThat(RelationshipLevel.Friend.higher).containsExactly(RelationshipLevel.BestFriend, RelationshipLevel.Partner).inOrder()
        assertThat(RelationshipLevel.Partner.lower).containsExactly(RelationshipLevel.BestFriend, RelationshipLevel.Friend).inOrder()
        assertThat(RelationshipLevel.Friend.lower).isEmpty()
        assertThat(RelationshipLevel.Friend.allowsAutomatic).isFalse()
        assertThat(RelationshipLevel.BestFriend.allowsAutomatic).isTrue()
        assertThat(RelationshipLevel.fromWire("nonsense")).isEqualTo(RelationshipLevel.Friend)
    }

    @Test
    fun `capability is neutral and defaults to unavailable`() {
        assertThat(Capability.fromWire("auto")).isEqualTo(Capability.Auto)
        assertThat(Capability.fromWire("ask")).isEqualTo(Capability.Ask)
        assertThat(Capability.fromWire(null)).isEqualTo(Capability.Unavailable)
        assertThat(Capability.fromWire("paused")).isEqualTo(Capability.Unavailable)
    }

    @Test
    fun `friend list rows decode from the server shape`() {
        val body = """[{"id":"a1","username":"sam","display_name":"Sam","level":"partner","label":"Love",
            "since":"2026-11-07T09:00:00+00:00","my_mode":"auto","they_allow":"ask",
            "invite":{"id":"r1","direction":"in","level":"partner","label":"Hubby"},"extra":1}]"""
        val friend = json.decodeFromString<List<Friend>>(body).single()
        assertThat(friend.levelValue).isEqualTo(RelationshipLevel.Partner)
        assertThat(friend.capability).isEqualTo(Capability.Ask)
        assertThat(friend.myModeValue).isEqualTo(WallpaperMode.Auto)
        assertThat(friend.invite?.incoming).isTrue()
        assertThat(friend.card.handle).isEqualTo("@sam")
    }

    @Test
    fun `permissions decode with nulls and defaults`() {
        val body = """{"contact_id":"a1","version":3,"wallpaper_mode":"paused","quiet_start":"22:00","quiet_end":"07:00",
            "reminder_auto_categories":["birthday"],"paused_until":null}"""
        val p = json.decodeFromString<ContactPermissions>(body)
        assertThat(p.mode).isEqualTo(WallpaperMode.Paused)
        assertThat(p.hasQuietHours).isTrue()
        assertThat(p.wallpaperDailyLimit).isEqualTo(5)
        assertThat(p.reminderAutoCategories).containsExactly("birthday")
    }

    @Test
    fun `inbox items decode and expose their data`() {
        val body = """{"id":"x","kind":"relationship_invite","created_at":"2026-11-07T09:00:00.123456+00:00",
            "read":false,"resolution":null,"subject_id":"r1","data":{"level":"partner","label":"Love"},
            "actor":{"id":"a1","username":"riya","display_name":"Riya"},"actionable":true}"""
        val item = json.decodeFromString<ActivityItem>(body)
        assertThat(item.kindValue).isEqualTo(ActivityKind.RelationshipInvite)
        assertThat(item.dataString("label")).isEqualTo("Love")
        assertThat(item.dataString("missing")).isNull()
        assertThat(item.createdInstant).isEqualTo(Instant.parse("2026-11-07T09:00:00.123456Z"))
        assertThat(ActivityKind.fromWire("from_the_future")).isEqualTo(ActivityKind.Unknown)
        assertThat(ActivityKind.fromWire("")).isEqualTo(ActivityKind.Unknown)
    }

    @Test
    fun `lookup replies decode for found and not found`() {
        val found = json.decodeFromString<LookupReply>("""{"status":"ok","user":{"id":"a","username":"sam","display_name":"Sam","relation":"requested"}}""")
        assertThat(found.user?.relationValue).isEqualTo(LookupRelation.Requested)
        val missing = json.decodeFromString<LookupReply>("""{"status":"ok","user":null}""")
        assertThat(missing.user).isNull()
    }

    @Test
    fun `invite links round trip`() {
        val link = InviteLinks.forUsername("@Riya.Cloud")
        assertThat(link).isEqualTo("meyouww://add/riya.cloud")
        assertThat(InviteLinks.parse(link)).isEqualTo("riya.cloud")
        assertThat(InviteLinks.parse("Add me on meyouww! meyouww://add/sam_21 see you")).isEqualTo("sam_21")
        assertThat(InviteLinks.parse("https://example.org/meyouww/add/tara?x=1")).isEqualTo("tara")
        assertThat(InviteLinks.parse("@Sam")).isEqualTo("sam")
        assertThat(InviteLinks.parse("sam")).isEqualTo("sam")
    }

    @Test
    fun `junk is not mistaken for an ID`() {
        assertThat(InviteLinks.parse("")).isNull()
        assertThat(InviteLinks.parse(null)).isNull()
        assertThat(InviteLinks.parse("https://evil.example/login")).isNull()
        assertThat(InviteLinks.parse("hello there")).isNull()
        assertThat(InviteLinks.parse("9lives")).isNull()
        assertThat(InviteLinks.parse("x".repeat(400))).isNull()
    }

    @Test
    fun `relative times`() {
        val now = Instant.parse("2026-11-07T12:00:00Z")
        assertThat(RelativeTime.between(now.minusSeconds(20), now)).isEqualTo(RelativeTime.JustNow)
        assertThat(RelativeTime.between(now.plusSeconds(60), now)).isEqualTo(RelativeTime.JustNow)
        assertThat(RelativeTime.between(now.minusSeconds(5 * 60), now)).isEqualTo(RelativeTime.Minutes(5))
        assertThat(RelativeTime.between(now.minusSeconds(3 * 3600), now)).isEqualTo(RelativeTime.Hours(3))
        assertThat(RelativeTime.between(now.minusSeconds(2 * 86400), now)).isEqualTo(RelativeTime.Days(2))
        assertThat(RelativeTime.between(now.minusSeconds(9 * 86400), now)).isInstanceOf(RelativeTime.OnDate::class.java)
    }

    @Test
    fun `change results map every server status`() {
        assertThat(ChangeResult.fromWire("needs_closer_level")).isEqualTo(ChangeResult.NeedsCloserLevel)
        assertThat(ChangeResult.fromWire("already_friends")).isEqualTo(ChangeResult.AlreadyFriends)
        assertThat(ChangeResult.fromWire("weird")).isEqualTo(ChangeResult.Invalid)
    }
}
