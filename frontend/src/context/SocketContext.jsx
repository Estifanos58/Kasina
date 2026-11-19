import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  useCallback,
} from "react";
import * as Stomp from "stompjs";
import SockJS from "sockjs-client";
import { toast } from "sonner";
import { useAppStore } from "@/store";
import { SOCKET_URL } from "@/utils/constants";
import {
  createReliableSocketManager,
  generateClientMessageId,
} from "@/lib/socket-manager";
import { getConversationKey } from "@/store/slices/chat-slice";

const SocketContext = createContext(null);

export const useSocket = () => {
  return useContext(SocketContext);
};

export const SocketProvider = ({ children }) => {
  const managerRef = useRef(null);
  const [connectionStatus, setConnectionStatus] = useState("disconnected");
  const [reconnectEpoch, setReconnectEpoch] = useState(0);
  const { userInfo } = useAppStore();

  useEffect(() => {
    if (!userInfo || !userInfo.id) {
      if (managerRef.current) {
        managerRef.current.disconnect();
        managerRef.current = null;
      }
      setConnectionStatus("disconnected");
      useAppStore.getState().setConnectionStatus("disconnected");
      return;
    }

    const manager = createReliableSocketManager({
      createClient: () => {
        const socket = new SockJS(SOCKET_URL);
        const client = Stomp.over(socket);
        client.debug = () => {};
        return client;
      },
      onStatusChange: (nextStatus) => {
        setConnectionStatus(nextStatus);
        useAppStore.getState().setConnectionStatus(nextStatus);
      },
      onDirectMessage: (message) => {
        if (!message) return;
        const store = useAppStore.getState();
        store.reconcileIncomingMessage(message);
        store.addContactsInDmContacts(message);
      },
      onChannelMessage: (message) => {
        if (!message) return;
        const store = useAppStore.getState();
        store.reconcileIncomingMessage(message);
        store.addChannelInChannelList(message);
      },
      onAck: (ack) => {
        if (!ack) return;
        useAppStore.getState().acknowledgeMessage(ack);
        if (ack.status === "FAILED" && ack.error) {
          toast.error(ack.error);
        }
      },
      onServerError: (err) => {
        if (err?.message) {
          toast.error(err.message);
        }
      },
      onOptimisticMessage: (optMsg) => {
        useAppStore.getState().enqueueOptimisticMessage(optMsg);
      },
      onMessageFailed: (clientMessageId, reason) => {
        useAppStore.getState().markMessageFailed(clientMessageId, reason);
      },
      onReconnected: () => {
        setReconnectEpoch((prev) => prev + 1);
      },
    });

    managerRef.current = manager;
    manager.connect();

    const handleOnline = () => {
      manager.connect();
    };
    window.addEventListener("online", handleOnline);

    return () => {
      window.removeEventListener("online", handleOnline);
      manager.disconnect();
      if (managerRef.current === manager) {
        managerRef.current = null;
      }
    };
  }, [userInfo]);

  const sendMessage = useCallback((message, explicitChatType) => {
    const store = useAppStore.getState();
    const currentUser = store.userInfo;
    const chatType = explicitChatType || store.selectedChatType;
    if (!currentUser?.id || !chatType || !message) return null;

    const clientMessageId = message.clientMessageId || generateClientMessageId();
    const targetId =
      chatType === "channel" ? message.channelId : message.recipient;
    const conversationKey = getConversationKey(chatType, targetId);

    const payload =
      chatType === "channel"
        ? {
            channelId: message.channelId,
            content: message.content,
            messageType: message.messageType,
            fileUrl: message.fileUrl,
            clientMessageId,
          }
        : {
            recipient: message.recipient,
            content: message.content,
            messageType: message.messageType,
            fileUrl: message.fileUrl,
            clientMessageId,
          };

    const optimisticMessage = {
      id: null,
      clientMessageId,
      sender: currentUser.id,
      senderName: currentUser.firstname || currentUser.firstName || currentUser.email,
      color: currentUser.color,
      image: currentUser.image,
      recipient: chatType === "contact" ? message.recipient : null,
      channelId: chatType === "channel" ? message.channelId : null,
      content: message.content,
      fileUrl: message.fileUrl,
      messageType: message.messageType,
      timestamps: new Date().toISOString(),
      conversationKey,
      chatType,
      status: "pending",
    };

    if (!managerRef.current) {
      store.enqueueOptimisticMessage(optimisticMessage);
      store.markMessageFailed(
        clientMessageId,
        "Messaging connection unavailable. Tap retry once online."
      );
      return clientMessageId;
    }

    return managerRef.current.send({
      chatType,
      payload,
      optimisticMessage,
    });
  }, []);

  const retryMessage = useCallback((clientMessageId) => {
    const store = useAppStore.getState();
    const target = store.markMessagePendingForRetry(clientMessageId);
    if (!target || !managerRef.current) return;

    const chatType = target.channelId ? "channel" : "contact";
    const payload =
      chatType === "channel"
        ? {
            channelId: target.channelId,
            content: target.content,
            messageType: target.messageType,
            fileUrl: target.fileUrl,
            clientMessageId: target.clientMessageId,
          }
        : {
            recipient: target.recipient,
            content: target.content,
            messageType: target.messageType,
            fileUrl: target.fileUrl,
            clientMessageId: target.clientMessageId,
          };

    managerRef.current.retry({ chatType, payload });
  }, []);

  const isConnected = connectionStatus === "connected";

  return (
    <SocketContext.Provider
      value={{
        sendMessage,
        retryMessage,
        isConnected,
        connectionStatus,
        reconnectEpoch,
      }}
    >
      {children}
    </SocketContext.Provider>
  );
};
