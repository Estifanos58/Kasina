import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createReliableSocketManager } from "../src/lib/socket-manager.js";

function createFakeStompClient() {
  const sentFrames = [];
  const subscriptions = new Map();
  let connectHeaders = null;
  let onConnectCb = null;
  let onErrorCb = null;

  const client = {
    connected: false,
    heartbeat: { outgoing: 0, incoming: 0 },
    connect(headers, onConnect, onError) {
      connectHeaders = headers;
      onConnectCb = onConnect;
      onErrorCb = onError;
    },
    subscribe(destination, callback) {
      subscriptions.set(destination, callback);
      return {
        destination,
        unsubscribe() {
          subscriptions.delete(destination);
        },
      };
    },
    send(destination, headers, body) {
      if (!client.connected) {
        throw new Error("Not connected");
      }
      sentFrames.push({ destination, headers, body: JSON.parse(body) });
    },
    disconnect(cb) {
      client.connected = false;
      if (cb) cb();
    },
    // Test helpers
    triggerConnect() {
      client.connected = true;
      onConnectCb?.();
    },
    triggerDisconnect(err = "Connection lost") {
      client.connected = false;
      onErrorCb?.(err);
    },
    emit(destination, payload) {
      const cb = subscriptions.get(destination);
      if (cb) cb({ body: JSON.stringify(payload) });
    },
    getSentFrames: () => sentFrames,
    getConnectHeaders: () => connectHeaders,
    getSubscriptionDestinations: () => Array.from(subscriptions.keys()),
  };

  return client;
}

describe("Reliable WebSocket Manager Tests", () => {
  it("connects without insecure username header, sets heartbeats, and avoids /topic/public", () => {
    const fakeClient = createFakeStompClient();
    const statuses = [];

    const manager = createReliableSocketManager({
      createClient: () => fakeClient,
      onStatusChange: (s) => statuses.push(s),
      heartbeatMs: 10000,
    });

    manager.connect();
    fakeClient.triggerConnect();

    assert.deepEqual(fakeClient.getConnectHeaders(), {});
    assert.equal("username" in fakeClient.getConnectHeaders(), false);
    assert.equal(fakeClient.heartbeat.outgoing, 10000);
    assert.equal(fakeClient.heartbeat.incoming, 10000);

    const subs = fakeClient.getSubscriptionDestinations();
    assert.ok(subs.includes("/user/queue/messages"));
    assert.ok(subs.includes("/user/queue/channel-messages"));
    assert.ok(subs.includes("/user/queue/ack"));
    assert.ok(subs.includes("/user/queue/errors"));
    assert.equal(subs.includes("/topic/public"), false);
    assert.equal(manager.getStatus(), "connected");

    manager.disconnect();
  });

  it("queues messages sent while disconnected and flushes them automatically upon reconnect", async () => {
    const clients = [];
    const optimisticMessages = [];
    const ackedMessages = [];
    let reconnectCount = 0;

    const manager = createReliableSocketManager({
      createClient: () => {
        const c = createFakeStompClient();
        clients.push(c);
        return c;
      },
      onOptimisticMessage: (m) => optimisticMessages.push(m),
      onAck: (a) => ackedMessages.push(a),
      onReconnected: () => {
        reconnectCount += 1;
      },
      baseReconnectDelayMs: 20,
      maxReconnectDelayMs: 40,
      ackTimeoutMs: 500,
    });

    // Initial connect
    manager.connect();
    clients[0].triggerConnect();
    assert.equal(reconnectCount, 0);

    // Simulate network drop
    clients[0].triggerDisconnect("Socket closed");
    assert.equal(manager.getStatus(), "reconnecting");

    // User sends a message while disconnected -> must be queued, NOT lost
    const cid = manager.send({
      chatType: "contact",
      payload: { recipient: "user-2", content: "Offline message", messageType: "text" },
      optimisticMessage: { recipient: "user-2", content: "Offline message", messageType: "text" },
    });

    assert.equal(optimisticMessages.length, 1);
    assert.equal(optimisticMessages[0].clientMessageId, cid);
    assert.equal(manager.getQueuedCount(), 1);

    // Wait for reconnect attempt to instantiate second client
    await new Promise((r) => setTimeout(r, 60));
    assert.ok(clients.length >= 2);

    const reconnectedClient = clients[clients.length - 1];
    reconnectedClient.triggerConnect();

    // Must notify onReconnected and automatically flush the queued message
    assert.equal(reconnectCount, 1);
    const sent = reconnectedClient.getSentFrames();
    assert.equal(sent.length, 1);
    assert.equal(sent[0].destination, "/app/chat.sendMessage");
    assert.equal(sent[0].body.clientMessageId, cid);
    assert.equal(sent[0].body.content, "Offline message");

    // Simulate server ACK -> queue clears
    reconnectedClient.emit("/user/queue/ack", {
      clientMessageId: cid,
      messageId: "srv-99",
      status: "DELIVERED",
    });
    assert.equal(ackedMessages.length, 1);
    assert.equal(manager.getQueuedCount(), 0);

    manager.disconnect();
  });

  it("retries unacknowledged messages and marks them failed after maxAutoRetries", async () => {
    const fakeClient = createFakeStompClient();
    const failures = [];

    const manager = createReliableSocketManager({
      createClient: () => fakeClient,
      onMessageFailed: (cid, reason) => failures.push({ cid, reason }),
      ackTimeoutMs: 25,
      maxAutoRetries: 2,
    });

    manager.connect();
    fakeClient.triggerConnect();

    const cid = manager.send({
      chatType: "channel",
      payload: { channelId: "chan-1", content: "Needs ack", messageType: "text" },
    });

    // Wait long enough for initial attempt + 2 automatic retries
    await new Promise((r) => setTimeout(r, 120));

    // Initial send + 2 retries = 3 frames with identical clientMessageId (for server-side deduplication)
    const sent = fakeClient.getSentFrames();
    assert.equal(sent.length, 3);
    assert.ok(sent.every((f) => f.body.clientMessageId === cid));
    assert.equal(failures.length, 1);
    assert.equal(failures[0].cid, cid);
    assert.equal(manager.getQueuedCount(), 0);

    // Manual retry reuses the same clientMessageId
    manager.retry({
      chatType: "channel",
      payload: { channelId: "chan-1", content: "Needs ack", messageType: "text", clientMessageId: cid },
    });
    assert.equal(fakeClient.getSentFrames().length, 4);
    assert.equal(fakeClient.getSentFrames()[3].body.clientMessageId, cid);

    manager.disconnect();
  });
});
