import { useAppStore } from "@/store";
import { createContext, useContext, useEffect, useRef, useState, useCallback } from "react";
import * as Stomp from "stompjs";
import SockJS from "sockjs-client";
import { SOCKET_URL } from "@/utils/constants";

const SocketContext = createContext(null);

export const useSocket = () => {
  return useContext(SocketContext);
};

export const SocketProvider = ({ children }) => {
  const stompClient = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const isConnectingRef = useRef(false);
  const [isConnected, setIsConnected] = useState(false);
  const { userInfo } = useAppStore();

  const handleReceiveMessage = useCallback((message) => {
    if (!message) return;
    const { selectedChatType, selectedChatData, addMessage, addContactsInDmContacts } =
      useAppStore.getState();

    // Check if message belongs to currently open DM
    if (
      selectedChatType === "contact" &&
      selectedChatData &&
      (selectedChatData.id === message.sender || selectedChatData.id === message.recipient)
    ) {
      addMessage(message);
    }

    // Always update DM contact list ordering
    addContactsInDmContacts(message);
  }, []);

  const handleChannelReceive = useCallback((message) => {
    if (!message) return;
    const { selectedChatType, selectedChatData, addMessage, addChannelInChannelList } =
      useAppStore.getState();

    // Check if message belongs to currently open channel
    if (
      selectedChatType === "channel" &&
      selectedChatData &&
      selectedChatData.id === message.channelId
    ) {
      addMessage(message);
    }

    // Always move active channel to top of channel list
    addChannelInChannelList(message);
  }, []);

  const connectSocket = useCallback(() => {
    if (!userInfo || !userInfo.id || isConnectingRef.current) {
      return;
    }

    if (stompClient.current && stompClient.current.connected) {
      return;
    }

    isConnectingRef.current = true;

    try {
      const socket = new SockJS(SOCKET_URL);
      const client = Stomp.over(socket);

      // Disable noisy STOMP debug logs in production/development
      client.debug = () => {};

      const headers = { username: userInfo.id };

      client.connect(
        headers,
        () => {
          isConnectingRef.current = false;
          setIsConnected(true);
          reconnectAttemptsRef.current = 0;
          stompClient.current = client;

          // Subscribe to user-specific private messages
          client.subscribe("/user/queue/messages", (message) => {
            try {
              handleReceiveMessage(JSON.parse(message.body));
            } catch (err) {
              console.error("Failed to parse direct message:", err);
            }
          });

          // Subscribe to channel messages delivered to user queue
          client.subscribe("/user/queue/channel-messages", (message) => {
            try {
              handleChannelReceive(JSON.parse(message.body));
            } catch (err) {
              console.error("Failed to parse channel message:", err);
            }
          });

          // Legacy topic subscription fallback
          client.subscribe("/topic/public", (message) => {
            try {
              handleReceiveMessage(JSON.parse(message.body));
            } catch (err) {
              console.error("Failed to parse public message:", err);
            }
          });
        },
        (_error) => {
          isConnectingRef.current = false;
          setIsConnected(false);

          // Exponential backoff reconnect
          const backoff = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 15000);
          reconnectAttemptsRef.current += 1;

          if (reconnectTimeoutRef.current) {
            clearTimeout(reconnectTimeoutRef.current);
          }
          reconnectTimeoutRef.current = setTimeout(() => {
            connectSocket();
          }, backoff);
        }
      );
    } catch (_err) {
      isConnectingRef.current = false;
      setIsConnected(false);
    }
  }, [userInfo, handleReceiveMessage, handleChannelReceive]);

  useEffect(() => {
    if (userInfo && userInfo.id) {
      connectSocket();
    } else {
      if (stompClient.current && stompClient.current.connected) {
        stompClient.current.disconnect();
      }
      setIsConnected(false);
    }

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (stompClient.current) {
        try {
          stompClient.current.disconnect();
        } catch (_ignored) {
          // Ignore disconnect error on unmount
        }
      }
    };
  }, [userInfo, connectSocket]);

  const sendMessage = useCallback((message) => {
    if (stompClient.current && stompClient.current.connected) {
      const { selectedChatType } = useAppStore.getState();

      if (selectedChatType === "channel") {
        stompClient.current.send(
          "/app/chat.sendChannelMessage",
          {},
          JSON.stringify(message)
        );
      } else {
        stompClient.current.send(
          "/app/chat.sendMessage",
          {},
          JSON.stringify(message)
        );
      }
    } else {
      console.warn("WebSocket is not connected. Attempting reconnect...");
      connectSocket();
    }
  }, [connectSocket]);

  return (
    <SocketContext.Provider value={{ sendMessage, isConnected }}>
      {children}
    </SocketContext.Provider>
  );
};
