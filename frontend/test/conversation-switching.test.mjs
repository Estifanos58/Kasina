import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  createChatSlice,
  getConversationKey,
} from "../src/store/slices/chat-slice.js";

function createTestStore(initialState = {}) {
  let state = {};
  const get = () => state;
  const set = (partial) => {
    state =
      typeof partial === "function"
        ? { ...state, ...partial(state) }
        : { ...state, ...partial };
  };
  const slice = createChatSlice(set, get);
  state = { ...slice, ...initialState };
  return { get, set, slice: state };
}

describe("Conversation Switching & Race Condition Guard Tests", () => {
  it("ignores stale async message responses after rapidly switching conversations", () => {
    const { get } = createTestStore({ userInfo: { id: "me-1" } });

    // User opens Contact A
    get().selectConversation("contact", { id: "user-A", firstname: "Alice" });
    const convKeyA = get().activeConversationKey;
    assert.equal(convKeyA, "contact:user-A");
    assert.equal(get().messagesLoading, true);

    // Before Contact A finishes loading, user switches to Contact B
    get().selectConversation("contact", { id: "user-B", firstname: "Bob" });
    const convKeyB = get().activeConversationKey;
    assert.equal(convKeyB, "contact:user-B");

    // Contact B finishes loading fast
    const committedB = get().setConversationMessages(
      convKeyB,
      [{ id: "msg-B1", sender: "user-B", recipient: "me-1", content: "Hi from Bob", timestamps: "2026-11-18T10:00:00Z" }],
      { hasMore: false, nextCursor: null }
    );
    assert.equal(committedB, true);
    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].id, "msg-B1");

    // Stale Contact A response resolves late -> MUST be ignored
    const committedA = get().setConversationMessages(
      convKeyA,
      [{ id: "msg-A1", sender: "user-A", recipient: "me-1", content: "Late Alice msg", timestamps: "2026-11-18T09:00:00Z" }],
      { hasMore: true, nextCursor: "stale-cursor" }
    );
    assert.equal(committedA, false);
    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].id, "msg-B1");
    assert.equal(get().hasMoreMessages, false);
    assert.equal(get().nextCursor, null);
  });

  it("ignores stale pagination responses from a previously active conversation", () => {
    const { get } = createTestStore({ userInfo: { id: "me-1" } });

    get().selectConversation("channel", { id: "chan-1", name: "General" });
    const chan1Key = getConversationKey("channel", "chan-1");
    get().setConversationMessages(
      chan1Key,
      [{ id: "c1-2", channelId: "chan-1", sender: "u2", content: "Latest", timestamps: "2026-11-18T11:00:00Z" }],
      { hasMore: true, nextCursor: "cursor-1" }
    );

    // Switch to Channel 2 while Channel 1 pagination is in flight
    get().selectConversation("channel", { id: "chan-2", name: "Dev" });
    const chan2Key = getConversationKey("channel", "chan-2");
    get().setConversationMessages(
      chan2Key,
      [{ id: "c2-1", channelId: "chan-2", sender: "u3", content: "Dev chat", timestamps: "2026-11-18T12:00:00Z" }],
      { hasMore: false, nextCursor: null }
    );

    // Late older-page response for Channel 1 arrives
    const prepended = get().prependOlderMessages(
      chan1Key,
      [{ id: "c1-1", channelId: "chan-1", sender: "u2", content: "Older", timestamps: "2026-11-18T10:00:00Z" }],
      { hasMore: false, nextCursor: null }
    );
    assert.equal(prepended, false);
    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].id, "c2-1");
  });

  it("preserves pending and failed outbox messages when switching away and back", () => {
    const { get } = createTestStore({ userInfo: { id: "me-1" } });

    get().selectConversation("contact", { id: "user-A" });
    get().setConversationMessages("contact:user-A", [], { hasMore: false, nextCursor: null });

    // Queue an optimistic message in Contact A
    get().enqueueOptimisticMessage({
      clientMessageId: "client-pending-1",
      sender: "me-1",
      recipient: "user-A",
      content: "Queued while offline",
      messageType: "text",
      conversationKey: "contact:user-A",
      timestamps: "2026-11-18T12:00:00Z",
    });
    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].status, "pending");

    // Switch to Contact B -> Contact A's pending message is NOT shown in Contact B
    get().selectConversation("contact", { id: "user-B" });
    assert.equal(get().selectedChatMessages.length, 0);

    // Switch back to Contact A -> pending message is preserved and visible
    get().selectConversation("contact", { id: "user-A" });
    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].clientMessageId, "client-pending-1");
  });

  it("reconciles optimistic message with server message and ACK without duplication", () => {
    const { get } = createTestStore({ userInfo: { id: "me-1" } });

    get().selectConversation("contact", { id: "user-A" });
    get().setConversationMessages("contact:user-A", [], { hasMore: false, nextCursor: null });

    get().enqueueOptimisticMessage({
      clientMessageId: "cid-100",
      sender: "me-1",
      recipient: "user-A",
      content: "Hello",
      messageType: "text",
      conversationKey: "contact:user-A",
      timestamps: "2026-11-18T12:00:00Z",
    });

    // Server broadcasts saved message containing the same clientMessageId
    get().reconcileIncomingMessage({
      id: "srv-msg-1",
      clientMessageId: "cid-100",
      sender: "me-1",
      recipient: "user-A",
      content: "Hello",
      messageType: "text",
      timestamps: "2026-11-18T12:00:01Z",
    });

    assert.equal(get().selectedChatMessages.length, 1);
    assert.equal(get().selectedChatMessages[0].id, "srv-msg-1");
    assert.equal(get().selectedChatMessages[0].status, "sent");
    assert.equal(get().pendingOutbox["cid-100"], undefined);

    // Subsequent ACK arrives -> still 1 message, no duplicate
    get().acknowledgeMessage({
      clientMessageId: "cid-100",
      messageId: "srv-msg-1",
      status: "DELIVERED",
      timestamps: "2026-11-18T12:00:01Z",
    });
    assert.equal(get().selectedChatMessages.length, 1);
  });

  it("prepends older messages in chronological order without duplicates", () => {
    const { get } = createTestStore({ userInfo: { id: "me-1" } });

    get().selectConversation("contact", { id: "user-A" });
    get().setConversationMessages(
      "contact:user-A",
      [
        { id: "m3", sender: "me-1", recipient: "user-A", content: "Three", timestamps: "2026-11-18T10:03:00Z" },
        { id: "m4", sender: "user-A", recipient: "me-1", content: "Four", timestamps: "2026-11-18T10:04:00Z" },
      ],
      { hasMore: true, nextCursor: "cur-3" }
    );

    const ok = get().prependOlderMessages(
      "contact:user-A",
      [
        { id: "m1", sender: "me-1", recipient: "user-A", content: "One", timestamps: "2026-11-18T10:01:00Z" },
        { id: "m2", sender: "user-A", recipient: "me-1", content: "Two", timestamps: "2026-11-18T10:02:00Z" },
        { id: "m3", sender: "me-1", recipient: "user-A", content: "Three", timestamps: "2026-11-18T10:03:00Z" }, // overlap
      ],
      { hasMore: false, nextCursor: null }
    );

    assert.equal(ok, true);
    assert.deepEqual(
      get().selectedChatMessages.map((m) => m.id),
      ["m1", "m2", "m3", "m4"]
    );
    assert.equal(get().hasMoreMessages, false);
  });
});
