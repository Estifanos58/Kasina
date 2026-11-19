/**
 * Framework-agnostic WebSocket STOMP reliability controller.
 * Handles JWT-cookie authenticated connections (no insecure username headers),
 * heartbeats, exponential backoff reconnection, outbox queueing during disconnects,
 * ACK timeouts, automatic retries, and duplicate-free subscriptions.
 */
export function generateClientMessageId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export function createReliableSocketManager({
  createClient,
  onStatusChange = () => {},
  onDirectMessage = () => {},
  onChannelMessage = () => {},
  onAck = () => {},
  onServerError = () => {},
  onOptimisticMessage = () => {},
  onMessageFailed = () => {},
  onReconnected = () => {},
  heartbeatMs = 10000,
  ackTimeoutMs = 10000,
  maxAutoRetries = 2,
  baseReconnectDelayMs = 1000,
  maxReconnectDelayMs = 15000,
}) {
  let client = null;
  let subscriptions = [];
  let status = "disconnected";
  let reconnectTimer = null;
  let reconnectAttempts = 0;
  let intentionalDisconnect = false;
  let hasConnectedOnce = false;

  // Map<clientMessageId, { destination, payload, retries, timerId, inFlight }>
  const queue = new Map();

  function setStatus(nextStatus) {
    if (status !== nextStatus) {
      status = nextStatus;
      onStatusChange(nextStatus);
    }
  }

  function clearSubscriptions() {
    for (const sub of subscriptions) {
      try {
        sub?.unsubscribe?.();
      } catch (_err) {
        // Ignore unsubscribe error on closed socket
      }
    }
    subscriptions = [];
  }

  function clearAckTimer(entry) {
    if (entry && entry.timerId) {
      clearTimeout(entry.timerId);
      entry.timerId = null;
    }
  }

  function scheduleReconnect() {
    if (intentionalDisconnect) return;
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
    }
    const expDelay = Math.min(
      baseReconnectDelayMs * Math.pow(2, reconnectAttempts),
      maxReconnectDelayMs
    );
    const jitter = Math.floor(Math.random() * Math.min(500, expDelay * 0.2));
    reconnectAttempts += 1;
    setStatus("reconnecting");
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, expDelay + jitter);
  }

  function handleAck(ack) {
    if (!ack || !ack.clientMessageId) {
      onAck(ack);
      return;
    }
    const entry = queue.get(ack.clientMessageId);
    if (entry) {
      clearAckTimer(entry);
      if (ack.status === "DELIVERED" || ack.status === "FAILED") {
        queue.delete(ack.clientMessageId);
      }
    }
    onAck(ack);
  }

  function handleIncomingPayload(message, handler) {
    if (message?.clientMessageId && queue.has(message.clientMessageId)) {
      const entry = queue.get(message.clientMessageId);
      clearAckTimer(entry);
      queue.delete(message.clientMessageId);
    }
    handler(message);
  }

  function transmitEntry(clientMessageId) {
    const entry = queue.get(clientMessageId);
    if (!entry) return;
    if (!client || !client.connected) {
      entry.inFlight = false;
      return;
    }

    clearAckTimer(entry);
    entry.inFlight = true;

    try {
      client.send(entry.destination, {}, JSON.stringify(entry.payload));
    } catch (_err) {
      entry.inFlight = false;
      scheduleReconnect();
      return;
    }

    entry.timerId = setTimeout(() => {
      const current = queue.get(clientMessageId);
      if (!current) return;
      current.inFlight = false;
      current.timerId = null;

      if (current.retries < maxAutoRetries) {
        current.retries += 1;
        if (client && client.connected) {
          transmitEntry(clientMessageId);
        } else {
          connect();
        }
      } else {
        queue.delete(clientMessageId);
        onMessageFailed(
          clientMessageId,
          "Delivery timed out. Tap retry to send again."
        );
      }
    }, ackTimeoutMs);
  }

  function flushQueue() {
    if (!client || !client.connected) return;
    for (const [clientMessageId, entry] of queue.entries()) {
      if (!entry.inFlight) {
        transmitEntry(clientMessageId);
      }
    }
  }

  function connect() {
    if (status === "connecting" || (client && client.connected)) {
      return;
    }

    intentionalDisconnect = false;
    setStatus(hasConnectedOnce ? "reconnecting" : "connecting");

    try {
      const nextClient = createClient();
      if (nextClient.heartbeat) {
        nextClient.heartbeat.outgoing = heartbeatMs;
        nextClient.heartbeat.incoming = heartbeatMs;
      }
      client = nextClient;

      // Authenticate strictly via HttpOnly JWT cookie during WebSocket handshake (no username header)
      nextClient.connect(
        {},
        () => {
          if (client !== nextClient || intentionalDisconnect) {
            try {
              nextClient.disconnect(() => {});
            } catch (_ignored) {
              // Ignore stale disconnect error
            }
            return;
          }

          const wasReconnect = hasConnectedOnce;
          hasConnectedOnce = true;
          reconnectAttempts = 0;
          setStatus("connected");

          clearSubscriptions();

          subscriptions.push(
            nextClient.subscribe("/user/queue/messages", (frame) => {
              try {
                handleIncomingPayload(JSON.parse(frame.body), onDirectMessage);
              } catch (err) {
                console.error("Failed to parse direct message:", err);
              }
            })
          );

          subscriptions.push(
            nextClient.subscribe("/user/queue/channel-messages", (frame) => {
              try {
                handleIncomingPayload(JSON.parse(frame.body), onChannelMessage);
              } catch (err) {
                console.error("Failed to parse channel message:", err);
              }
            })
          );

          subscriptions.push(
            nextClient.subscribe("/user/queue/ack", (frame) => {
              try {
                handleAck(JSON.parse(frame.body));
              } catch (err) {
                console.error("Failed to parse message acknowledgement:", err);
              }
            })
          );

          subscriptions.push(
            nextClient.subscribe("/user/queue/errors", (frame) => {
              try {
                onServerError(JSON.parse(frame.body));
              } catch (err) {
                console.error("Failed to parse WebSocket error frame:", err);
              }
            })
          );

          flushQueue();

          if (wasReconnect) {
            onReconnected();
          }
        },
        (error) => {
          if (client !== nextClient) return;
          clearSubscriptions();

          // Mark in-flight entries as queued so they flush on reconnect
          for (const entry of queue.values()) {
            clearAckTimer(entry);
            entry.inFlight = false;
          }

          if (intentionalDisconnect) {
            setStatus("disconnected");
            return;
          }

          const errText = typeof error === "string" ? error : error?.headers?.message || "";
          if (errText.toLowerCase().includes("authentication required") || errText.includes("401")) {
            setStatus("disconnected");
            return;
          }

          scheduleReconnect();
        }
      );
    } catch (_err) {
      if (!intentionalDisconnect) {
        scheduleReconnect();
      } else {
        setStatus("disconnected");
      }
    }
  }

  function disconnect() {
    intentionalDisconnect = true;
    hasConnectedOnce = false;
    reconnectAttempts = 0;

    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }

    for (const entry of queue.values()) {
      clearAckTimer(entry);
    }
    queue.clear();
    clearSubscriptions();

    if (client) {
      const currentClient = client;
      client = null;
      try {
        if (currentClient.connected) {
          currentClient.disconnect(() => {});
        }
      } catch (_ignored) {
        // Ignore disconnect errors on teardown
      }
    }

    setStatus("disconnected");
  }

  function send({ chatType, payload, optimisticMessage }) {
    const clientMessageId = payload.clientMessageId || generateClientMessageId();
    const enrichedPayload = {
      ...payload,
      clientMessageId,
    };

    const destination =
      chatType === "channel"
        ? "/app/chat.sendChannelMessage"
        : "/app/chat.sendMessage";

    if (optimisticMessage) {
      onOptimisticMessage({
        ...optimisticMessage,
        clientMessageId,
      });
    }

    queue.set(clientMessageId, {
      destination,
      payload: enrichedPayload,
      retries: 0,
      timerId: null,
      inFlight: false,
    });

    if (client && client.connected) {
      transmitEntry(clientMessageId);
    } else {
      connect();
    }

    return clientMessageId;
  }

  function retry({ chatType, payload }) {
    if (!payload || !payload.clientMessageId) return null;
    return send({ chatType, payload });
  }

  return {
    connect,
    disconnect,
    send,
    retry,
    getStatus: () => status,
    getQueuedCount: () => queue.size,
  };
}
