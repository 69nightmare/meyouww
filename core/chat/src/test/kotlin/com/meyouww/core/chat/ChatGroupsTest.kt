package com.meyouww.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatGroupsTest {
    private val sent = ChatRepository.STATUS_SENT
    private val delivered = ChatRepository.STATUS_DELIVERED
    private val read = ChatRepository.STATUS_READ

    @Test
    fun `group light goes yellow when everyone has it and green when everyone saw it`() {
        var r = mapOf("sam" to sent, "tara" to sent)
        r = GroupReceipts.advance(r, "sam", delivered)
        assertThat(GroupReceipts.status(r, sent)).isEqualTo(sent)
        r = GroupReceipts.advance(r, "tara", read)
        assertThat(GroupReceipts.status(r, sent)).isEqualTo(delivered)
        r = GroupReceipts.advance(r, "sam", read)
        assertThat(GroupReceipts.status(r, sent)).isEqualTo(read)
    }

    @Test
    fun `a receipt never moves back and only counts for people it went to`() {
        val r = GroupReceipts.advance(mapOf("sam" to read), "sam", delivered)
        assertThat(r["sam"]).isEqualTo(read)
        assertThat(GroupReceipts.advance(r, "newcomer", read)).isEqualTo(r)
        assertThat(GroupReceipts.decode(GroupReceipts.encode(r))).isEqualTo(r)
        assertThat(GroupReceipts.status(emptyMap(), sent)).isEqualTo(sent)
    }

    @Test
    fun `poll counts each person once per option`() {
        val poll = PollState(listOf("Pizza", "Momos", "Both"), multi = true, votes = mapOf("a" to listOf(0, 2), "b" to listOf(2)))
        assertThat(poll.counts()).containsExactly(1, 0, 2).inOrder()
        assertThat(PollState.decode(poll.encode())).isEqualTo(poll)
    }

    @Test
    fun `poll choices are trimmed, distinct and two to eight`() {
        assertThat(ChatRepository.cleanOptions(listOf(" Pizza ", "Pizza", "", "Momos"))).containsExactly("Pizza", "Momos").inOrder()
        assertThat(ChatRepository.cleanOptions(listOf("Only one", " "))).isNull()
        assertThat(ChatRepository.cleanOptions((1..12).map { "Option $it" })).hasSize(PollState.MAX_OPTIONS)
    }

    @Test
    fun `new kinds travel encrypted and only polls and reminders notify`() {
        val poll = ChatPayload(id = "p", kind = ChatPayload.KIND_POLL, text = "Dinner?", options = listOf("A", "B"), group = "g", sentAt = 1)
        assertThat(ChatPayload.decode(ChatPayload.encode(poll))).isEqualTo(poll)
        assertThat(poll.visible).isTrue()
        assertThat(ChatPayload(id = "v", kind = ChatPayload.KIND_VOTE, target = "p", choices = listOf(1), sentAt = 1).visible).isFalse()
        assertThat(ChatPayload(id = "g", kind = ChatPayload.KIND_GROUP_META, text = "Trip", sentAt = 1).visible).isFalse()
        assertThat(ChatPayload(id = "r", kind = ChatPayload.KIND_REMINDER, text = "Movie", start = "2026-11-20T18:30", sentAt = 1).visible).isTrue()
        // A 1:1 message from an older version carries no group.
        assertThat(ChatPayload.decode("""{"id":"t","kind":"text","text":"hi","sent_at":1}""")?.group).isNull()
    }

    @Test
    fun `a reminder remembers its calendar event`() {
        val r = ReminderState("2026-11-20T18:30", added = true, eventId = "e1")
        assertThat(ReminderState.decode(r.encode())).isEqualTo(r)
        assertThat(ReminderState.decode("""{"start":"2026-11-20T18:30"}""")).isEqualTo(ReminderState("2026-11-20T18:30"))
    }
}
