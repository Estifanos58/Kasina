import { GET_SPECIFIC_USER } from '../../utils/constants.js';
import { apiClient } from '../../lib/api-client.js';

export const createChatSlice = (set, get) => ({
  selectedChatType: undefined,
  selectedChatData: undefined,
  selectedChatMessages: [],
  directMessagesContacts: [],
  isUploading: false,
  isDownloading: false,
  fileUploadProgress: 0,
  fileDownloadProgress: 0,
  channels: [],

  setChannels: (channels) => set({ channels: channels || [] }),
  setIsUploading: (isUploading) => set({ isUploading }),
  setIsDownloading: (isDownloading) => set({ isDownloading }),
  setFileUploadProgress: (fileUploadProgress) => set({ fileUploadProgress }),
  setFileDownloadProgress: (fileDownloadProgress) => set({ fileDownloadProgress }),
  setSelectedChatType: (selectedChatType) => set({ selectedChatType }),
  setSelectedChatData: (selectedChatData) => set({ selectedChatData }),
  setSelectedChatMessages: (selectedChatMessages) => set({ selectedChatMessages: selectedChatMessages || [] }),
  setDirectMessagesContacts: (directMessagesContacts) => set({ directMessagesContacts: directMessagesContacts || [] }),

  addChannel: (channel) => {
    if (!channel) return;
    const channels = get().channels;
    set({ channels: [channel, ...channels] });
  },

  closedChat: () =>
    set({
      selectedChatType: undefined,
      selectedChatData: undefined,
      selectedChatMessages: [],
    }),

  addMessage: (message) => {
    if (!message) return;
    const selectedChatMessages = get().selectedChatMessages;

    // Avoid duplicates by checking the message ID if present
    const isDuplicate = message.id && selectedChatMessages.some((msg) => msg.id === message.id);

    if (!isDuplicate) {
      const updatedMessages = [...selectedChatMessages, message];

      // Sort messages by timestamps (support both timestamps and timestamp)
      updatedMessages.sort((a, b) => {
        const timeA = new Date(a.timestamps || a.timestamp || 0).getTime();
        const timeB = new Date(b.timestamps || b.timestamp || 0).getTime();
        return timeA - timeB;
      });

      set({ selectedChatMessages: updatedMessages });
    }
  },

  addChannelInChannelList: (message) => {
    if (!message || !message.channelId) return;
    const channels = [...get().channels];
    const index = channels.findIndex((channel) => channel.id === message.channelId);

    if (index !== -1) {
      const [channel] = channels.splice(index, 1);
      channels.unshift(channel);
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

    const dmContacts = [...get().directMessagesContacts];
    const index = dmContacts.findIndex((contact) => contact && contact.id === fromId);

    if (index !== -1) {
      const [contact] = dmContacts.splice(index, 1);
      contact.lastMessageTime = message.timestamps || message.timestamp || new Date().toISOString();
      dmContacts.unshift(contact);
      set({ directMessagesContacts: dmContacts });
    } else {
      try {
        const response = await apiClient.post(
          GET_SPECIFIC_USER,
          { userId: fromId },
          { withCredentials: true }
        );

        if (response.data) {
          const userData = response.data;
          userData.lastMessageTime = message.timestamps || message.timestamp || new Date().toISOString();
          dmContacts.unshift(userData);
          set({ directMessagesContacts: dmContacts });
        }
      } catch (error) {
        console.error("Error fetching user data:", error);
      }
    }
  },
});
