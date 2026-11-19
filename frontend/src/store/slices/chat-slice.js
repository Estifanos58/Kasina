import { GET_SPECIFIC_USER } from "../../utils/constants.js";
import { apiClient } from "../../lib/api-client.js";

const pendingUserLookups = new Map();

export function getConversationKey(chatType, chatDataOrId) {
  if (!chatType || !chatDataOrId) return null;
  const id = typeof chatDataOrId === "string" ? chatDataOrId : chatDataOrId.id;
  if (!id) return null;
  return `${chatType}:${id}`;
}

export function getMessageConversationKey(message, currentUserId) {
  if (!message) return null;
  if (message.channelId) {
    return `channel:${message.channelId}`;
  }
  const otherId =
    message.sender === currentUserId ? message.recipient : message.sender;
  return otherId ? `contact:${otherId}` : null;
}

function sortChronologically(messages) {
  return [...messages].sort((a, b) => {
    const timeA = new Date(a.timestamps || a.timestamp || 0).getTime();
    const timeB = new Date(b.timestamps || b.timestamp || 0).getTime();
    if (timeA !== timeB) {
      return timeA - timeB;
    }
    const idA = String(a.id || a.clientMessageId || "");
    const idB = String(b.id || b.clientMessageId || "");
    return idA.localeCompare(idB);
  });
}

function mergeUniqueMessages(existingMessages, incomingMessages) {
  const byId = new Map();
  const byClientId = new Map();
  const result = [];

  const upsert = (msg) => {
    if (!msg) return;
    let existingIndex = -1;

    if (msg.id && byId.has(msg.id)) {
      existingIndex = byId.get(msg.id);
    } else if (msg.clientMessageId && byClientId.has(msg.clientMessageId)) {
      existingIndex = byClientId.get(msg.clientMessageId);
    }

    if (existingIndex !== -1) {
      const merged = {
        ...result[existingIndex],
        ...msg,
        status: msg.id ? "sent" : msg.status || result[existingIndex].status,
        error: msg.id ? null : msg.error ?? result[existingIndex].error,
      };
      result[existingIndex] = merged;
      if (merged.id) byId.set(merged.id, existingIndex);
      if (merged.clientMessageId) byClientId.set(merged.clientMessageId, existingIndex);
    } else {
      const idx = result.length;
      result.push(msg);
      if (msg.id) byId.set(msg.id, idx);
      if (msg.clientMessageId) byClientId.set(msg.clientMessageId, idx);
    }
  };

  for (const m of existingMessages || []) upsert(m);
  for (const m of incomingMessages || []) upsert(m);

  return sortChronologically(result);
}

export const createChatSlice = (set, get) => ({
  selectedChatType: undefined,
  selectedChatData: undefined,
  activeConversationKey: null,
  selectedChatMessages: [],
  messagesLoading: false,
  messagesError: null,
  loadingOlderMessages: false,
  hasMoreMessages: false,
  nextCursor: null,

  pendingOutbox: {},
  connectionStatus: "disconnected",

  directMessagesContacts: [],
  contactsLoading: false,
  contactsError: null,

  channels: [],
  channelsLoading: false,
  channelsError: null,

  isUploading: false,
  isDownloading: false,
  fileUploadProgress: 0,
  fileDownloadProgress: 0,

  setChannels: (channels) => set({ channels: channels || [], channelsError: null }),
  setChannelsLoading: (channelsLoading) => set({ channelsLoading }),
  setChannelsError: (channelsError) => set({ channelsError }),

  setDirectMessagesContacts: (directMessagesContacts) =>
    set({ directMessagesContacts: directMessagesContacts || [], contactsError: null }),
  setContactsLoading: (contactsLoading) => set({ contactsLoading }),
  setContactsError: (contactsError) => set({ contactsError }),

  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  setIsUploading: (isUploading) => set({ isUploading }),
  setIsDownloading: (isDownloading) => set({ isDownloading }),
  setFileUploadProgress: (fileUploadProgress) => set({ fileUploadProgress }),
  setFileDownloadProgress: (fileDownloadProgress) => set({ fileDownloadProgress }),

  setSelectedChatType: (selectedChatType) => {
    const selectedChatData = get().selectedChatData;
    set({
      selectedChatType,
      activeConversationKey: getConversationKey(selectedChatType, selectedChatData),
    });
  },

  setSelectedChatData: (selectedChatData) => {
    const selectedChatType = get().selectedChatType;
    set({
      selectedChatData,
      activeConversationKey: getConversationKey(selectedChatType, selectedChatData),
    });
  },

  setSelectedChatMessages: (selectedChatMessages) =>
    set({ selectedChatMessages: selectedChatMessages || [] }),

  selectConversation: (chatType, chatData) => {
    const nextKey = getConversationKey(chatType, chatData);
    const currentKey = get().activeConversationKey;
    if (nextKey && nextKey === currentKey) {
      set({ selectedChatData: chatData });
      return;
    }

    // Re-attach any pending or failed outbox messages belonging to the target conversation
    const outboxForConversation = Object.values(get().pendingOutbox || {}).filter(
      (msg) => msg.conversationKey === nextKey
    );

    set({
      selectedChatType: chatType,
      selectedChatData: chatData,
      activeConversationKey: nextKey,
      selectedChatMessages: sortChronologically(outboxForConversation),
      messagesLoading: Boolean(nextKey),
      messagesError: null,
      loadingOlderMessages: false,
      hasMoreMessages: false,
      nextCursor: null,
    });
  },

  setMessagesLoading: (conversationKey, isLoading) => {
    if (conversationKey && conversationKey !== get().activeConversationKey) {
      return;
    }
    set({ messagesLoading: isLoading });
  },

  setMessagesError: (conversationKey, error) => {
    if (conversationKey && conversationKey !== get().activeConversationKey) {
      return;
    }
    set({ messagesError: error, messagesLoading: false });
  },

  setConversationMessages: (conversationKey, messages, pagination = {}) => {
    // Race-condition guard: ignore responses for conversations that are no longer active
    if (conversationKey !== get().activeConversationKey) {
      return false;
    }

    const outbox = { ...get().pendingOutbox };
    const receivedClientIds = new Set(
      (messages || []).map((m) => m?.clientMessageId).filter(Boolean)
    );

    // Clear any outbox item that is already persisted on the server
    for (const clientId of receivedClientIds) {
      delete outbox[clientId];
    }

    const remainingOutboxForConv = Object.values(outbox).filter(
      (msg) => msg.conversationKey === conversationKey
    );

    const normalizedServerMessages = (messages || []).map((m) => ({
      ...m,
      status: "sent",
    }));

    const merged = mergeUniqueMessages(
      normalizedServerMessages,
      remainingOutboxForConv
    );

    set({
      selectedChatMessages: merged,
      pendingOutbox: outbox,
      messagesLoading: false,
      messagesError: null,
      hasMoreMessages: Boolean(pagination.hasMore),
      nextCursor: pagination.nextCursor || null,
    });
    return true;
  },

  prependOlderMessages: (conversationKey, olderMessages, pagination = {}) => {
    if (conversationKey !== get().activeConversationKey) {
      return false;
    }
    const normalizedOlder = (olderMessages || []).map((m) => ({
      ...m,
      status: "sent",
    }));
    const merged = mergeUniqueMessages(
      normalizedOlder,
      get().selectedChatMessages
    );
    set({
      selectedChatMessages: merged,
      loadingOlderMessages: false,
      hasMoreMessages: Boolean(pagination.hasMore),
      nextCursor: pagination.nextCursor || null,
    });
    return true;
  },

  setLoadingOlderMessages: (conversationKey, loadingOlderMessages) => {
    if (conversationKey && conversationKey !== get().activeConversationKey) {
      return;
    }
    set({ loadingOlderMessages });
  },

  enqueueOptimisticMessage: (message) => {
    if (!message || !message.clientMessageId) return;
    const convKey =
      message.conversationKey ||
      getMessageConversationKey(message, get().userInfo?.id);

    const optimisticEntry = {
      ...message,
      conversationKey: convKey,
      status: "pending",
      error: null,
      timestamps: message.timestamps || new Date().toISOString(),
    };

    const nextOutbox = {
      ...get().pendingOutbox,
      [message.clientMessageId]: optimisticEntry,
    };

    if (convKey && convKey === get().activeConversationKey) {
      set({
        pendingOutbox: nextOutbox,
        selectedChatMessages: mergeUniqueMessages(get().selectedChatMessages, [
          optimisticEntry,
        ]),
      });
    } else {
      set({ pendingOutbox: nextOutbox });
    }
  },

  acknowledgeMessage: (ack) => {
    if (!ack || !ack.clientMessageId) return;
    const clientId = ack.clientMessageId;
    const outbox = { ...get().pendingOutbox };
    const existingOutbox = outbox[clientId];

    if (ack.status === "DELIVERED") {
      delete outbox[clientId];
      const updatedMessages = get().selectedChatMessages.map((msg) => {
        if (msg.clientMessageId === clientId) {
          return {
            ...msg,
            id: ack.messageId || msg.id,
            timestamps: ack.timestamps || msg.timestamps,
            status: "sent",
            error: null,
          };
        }
        return msg;
      });
      set({
        pendingOutbox: outbox,
        selectedChatMessages: mergeUniqueMessages(updatedMessages, []),
      });
    } else if (ack.status === "FAILED") {
      const errorMessage = ack.error || "Failed to deliver message";
      if (existingOutbox) {
        outbox[clientId] = {
          ...existingOutbox,
          status: "failed",
          error: errorMessage,
        };
      }
      const updatedMessages = get().selectedChatMessages.map((msg) =>
        msg.clientMessageId === clientId
          ? { ...msg, status: "failed", error: errorMessage }
          : msg
      );
      set({
        pendingOutbox: outbox,
        selectedChatMessages: updatedMessages,
      });
    }
  },

  markMessageFailed: (clientMessageId, error = "Message could not be sent") => {
    if (!clientMessageId) return;
    const outbox = { ...get().pendingOutbox };
    if (outbox[clientMessageId]) {
      outbox[clientMessageId] = {
        ...outbox[clientMessageId],
        status: "failed",
        error,
      };
    }
    const updatedMessages = get().selectedChatMessages.map((msg) =>
      msg.clientMessageId === clientMessageId
        ? { ...msg, status: "failed", error }
        : msg
    );
    set({
      pendingOutbox: outbox,
      selectedChatMessages: updatedMessages,
    });
  },

  markMessagePendingForRetry: (clientMessageId) => {
    if (!clientMessageId) return null;
    const outbox = { ...get().pendingOutbox };
    const target =
      outbox[clientMessageId] ||
      get().selectedChatMessages.find((m) => m.clientMessageId === clientMessageId);
    if (!target) return null;

    const refreshed = {
      ...target,
      status: "pending",
      error: null,
      retryCount: (target.retryCount || 0) + 1,
    };
    outbox[clientMessageId] = refreshed;

    const updatedMessages = get().selectedChatMessages.map((msg) =>
      msg.clientMessageId === clientMessageId ? refreshed : msg
    );
    set({
      pendingOutbox: outbox,
      selectedChatMessages: updatedMessages,
    });
    return refreshed;
  },

  reconcileIncomingMessage: (message) => {
    if (!message) return;
    const outbox = { ...get().pendingOutbox };
    if (message.clientMessageId && outbox[message.clientMessageId]) {
      delete outbox[message.clientMessageId];
      set({ pendingOutbox: outbox });
    }

    const currentUserId = get().userInfo?.id;
    const msgKey = getMessageConversationKey(message, currentUserId);
    if (msgKey && msgKey === get().activeConversationKey) {
      const normalized = { ...message, status: "sent", error: null };
      set({
        selectedChatMessages: mergeUniqueMessages(get().selectedChatMessages, [
          normalized,
        ]),
      });
    }
  },

  addChannel: (channel) => {
    if (!channel) return;
    const channels = get().channels;
    const filtered = channels.filter((c) => c.id !== channel.id);
    set({ channels: [channel, ...filtered] });
  },

  closedChat: () =>
    set({
      selectedChatType: undefined,
      selectedChatData: undefined,
      activeConversationKey: null,
      selectedChatMessages: [],
      messagesLoading: false,
      messagesError: null,
      loadingOlderMessages: false,
      hasMoreMessages: false,
      nextCursor: null,
    }),

  resetChatState: () =>
    set({
      selectedChatType: undefined,
      selectedChatData: undefined,
      activeConversationKey: null,
      selectedChatMessages: [],
      messagesLoading: false,
      messagesError: null,
      loadingOlderMessages: false,
      hasMoreMessages: false,
      nextCursor: null,
      pendingOutbox: {},
      connectionStatus: "disconnected",
      directMessagesContacts: [],
      contactsLoading: false,
      contactsError: null,
      channels: [],
      channelsLoading: false,
      channelsError: null,
      isUploading: false,
      isDownloading: false,
      fileUploadProgress: 0,
      fileDownloadProgress: 0,
    }),

  addMessage: (message) => {
    if (!message) return;
    const normalized = {
      ...message,
      status: message.id ? "sent" : message.status || "sent",
    };
    set({
      selectedChatMessages: mergeUniqueMessages(get().selectedChatMessages, [
        normalized,
      ]),
    });
  },

  addChannelInChannelList: (message) => {
    if (!message || !message.channelId) return;
    const channels = [...get().channels];
    const index = channels.findIndex((channel) => channel.id === message.channelId);

    if (index !== -1) {
      const [channel] = channels.splice(index, 1);
      channels.unshift({ ...channel, updatedAt: message.timestamps || new Date().toISOString() });
      set({ channels });
    }
  },

  addContactsInDmContacts: async (message) => {
    if (!message) return;
    const currentUser = get().userInfo;
    if (!currentUser || !currentUser.id) return;

    const userId = currentUser.id;
    const fromId = message.sender === userId ? message.recipient : message.sender;
    if (!fromId || fromId === userId) return;

    const msgTime = message.timestamps || message.timestamp || new Date().toISOString();
    const dmContacts = [...get().directMessagesContacts];
    const index = dmContacts.findIndex((contact) => contact && contact.id === fromId);

    if (index !== -1) {
      const updatedContact = {
        ...dmContacts[index],
        lastMessageTime: msgTime,
      };
      dmContacts.splice(index, 1);
      dmContacts.unshift(updatedContact);
      set({ directMessagesContacts: dmContacts });
    } else {
      if (pendingUserLookups.has(fromId)) {
        return pendingUserLookups.get(fromId);
      }

      const lookupPromise = apiClient
        .post(GET_SPECIFIC_USER, { userId: fromId })
        .then((response) => {
          if (response.data) {
            const latestContacts = [...get().directMessagesContacts];
            const existingIdx = latestContacts.findIndex((c) => c && c.id === fromId);
            const userData = {
              ...response.data,
              lastMessageTime: msgTime,
            };
            if (existingIdx !== -1) {
              latestContacts.splice(existingIdx, 1);
            }
            latestContacts.unshift(userData);
            set({ directMessagesContacts: latestContacts });
          }
        })
        .catch((error) => {
          console.error("Error fetching user data:", error);
        })
        .finally(() => {
          pendingUserLookups.delete(fromId);
        });

      pendingUserLookups.set(fromId, lookupPromise);
      return lookupPromise;
    }
  },
});
