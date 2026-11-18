import { useEffect, useState } from "react";
import { Search, X } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import {
  GET_DM_CONTACTS_ROUTES,
  GET_USER_CHANNELS_ROUTE,
  SEARCH_CONTACTS_ROUTES,
} from "@/utils/constants";
import { useAppStore } from "@/store";
import { useSocket } from "@/context/SocketContext";
import { getDisplayName } from "@/lib/utils";
import ContactList from "@/components/contact-list";
import KasinaLogo from "@/components/kasina-logo";
import UserAvatar from "@/components/user-avatar";
import ProfileInfo from "./components/profile-info";
import NewDm from "./components/new-dm";
import CreateChannel from "./components/create-channel";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const ContactContainer = () => {
  const [searchQuery, setSearchQuery] = useState("");
  const [searchedContacts, setSearchedContacts] = useState([]);
  const [isSearching, setIsSearching] = useState(false);

  const {
    setDirectMessagesContacts,
    directMessagesContacts,
    channels,
    setChannels,
    setSelectedChatType,
    setSelectedChatData,
    setSelectedChatMessages,
  } = useAppStore();

  const socket = useSocket();
  const isConnected = socket?.isConnected ?? false;

  useEffect(() => {
    const getContacts = async () => {
      try {
        const response = await apiClient.get(GET_DM_CONTACTS_ROUTES, {
          withCredentials: true,
        });
        if (response.data) {
          setDirectMessagesContacts(response.data);
        }
      } catch (err) {
        console.error("Failed to load contacts:", err);
      }
    };

    const getChannels = async () => {
      try {
        const response = await apiClient.get(GET_USER_CHANNELS_ROUTE, {
          withCredentials: true,
        });
        if (response.data) {
          setChannels(response.data);
        }
      } catch (err) {
        console.error("Failed to load channels:", err);
      }
    };

    getContacts();
    getChannels();
  }, [setChannels, setDirectMessagesContacts]);

  const searchContacts = async (searchTerm) => {
    setSearchQuery(searchTerm);
    const trimmed = searchTerm.trim();
    if (!trimmed) {
      setSearchedContacts([]);
      setIsSearching(false);
      return;
    }

    try {
      setIsSearching(true);
      const response = await apiClient.post(
        SEARCH_CONTACTS_ROUTES,
        { searchTerm: trimmed },
        { withCredentials: true }
      );

      if (response.status === 200 && Array.isArray(response.data)) {
        setSearchedContacts(response.data);
      } else {
        setSearchedContacts([]);
      }
    } catch (error) {
      console.error("Error searching contacts:", error);
      setSearchedContacts([]);
    } finally {
      setIsSearching(false);
    }
  };

  const selectNewContact = (contact) => {
    setSelectedChatMessages([]);
    setSelectedChatType("contact");
    setSelectedChatData(contact);
    setSearchQuery("");
    setSearchedContacts([]);
  };

  const clearSearch = () => {
    setSearchQuery("");
    setSearchedContacts([]);
  };

  return (
    <aside className="flex flex-col w-full h-full bg-[#111317] border-r border-white/[0.07]">
      {/* Top workspace header */}
      <div className="h-16 px-4 border-b border-white/[0.06] flex items-center justify-between shrink-0">
        <KasinaLogo size="sm" />
        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger asChild>
              <div className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-white/[0.04] border border-white/[0.06] text-[11px] font-medium text-zinc-400 cursor-default">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isConnected
                      ? "bg-emerald-500"
                      : "bg-amber-500 animate-pulse"
                  }`}
                />
                <span>{isConnected ? "Online" : "Syncing"}</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>
              {isConnected
                ? "Real-time connection active"
                : "Reconnecting to messaging server..."}
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      {/* Inline search */}
      <div className="p-3 shrink-0 relative">
        <div className="relative flex items-center">
          <Search className="h-4 w-4 text-zinc-500 absolute left-3 pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => searchContacts(e.target.value)}
            placeholder="Search people..."
            aria-label="Search people"
            className="w-full h-9 rounded-xl bg-[#181B22] border border-white/[0.07] pl-9 pr-8 text-[13px] text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/50 focus:ring-2 focus:ring-emerald-500/15"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={clearSearch}
              aria-label="Clear search"
              className="absolute right-2 p-1 rounded-md text-zinc-500 hover:text-zinc-200 transition-colors"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {/* Search results dropdown */}
        {searchQuery.trim().length > 0 && (
          <div className="absolute left-3 right-3 top-[54px] z-30 rounded-xl border border-white/[0.1] bg-[#161920] shadow-2xl overflow-hidden max-h-64 overflow-y-auto">
            {isSearching ? (
              <div className="px-3.5 py-4 text-xs text-zinc-400 text-center">
                Searching...
              </div>
            ) : searchedContacts.length > 0 ? (
              <div className="p-1.5 space-y-0.5">
                {searchedContacts.map((contact, index) => {
                  const name = getDisplayName(contact);
                  return (
                    <button
                      type="button"
                      key={contact.id || index}
                      onClick={() => selectNewContact(contact)}
                      className="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-left hover:bg-white/[0.06] transition-colors"
                    >
                      <UserAvatar user={contact} size="sm" />
                      <div className="min-w-0 flex-1">
                        <p className="text-[13px] font-medium text-zinc-100 truncate">
                          {name}
                        </p>
                        <p className="text-[11px] text-zinc-500 truncate">
                          {contact.email}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="px-3.5 py-4 text-xs text-zinc-500 text-center">
                No people found for &ldquo;{searchQuery.trim()}&rdquo;
              </div>
            )}
          </div>
        )}
      </div>

      {/* Scrollable DM & Channel sections */}
      <div className="flex-1 overflow-y-auto py-1 space-y-5">
        <section>
          <div className="flex items-center justify-between px-4 mb-1.5">
            <SectionTitle
              text="Direct messages"
              count={directMessagesContacts?.length}
            />
            <NewDm />
          </div>
          <ContactList contacts={directMessagesContacts} />
        </section>

        <section>
          <div className="flex items-center justify-between px-4 mb-1.5">
            <SectionTitle text="Channels" count={channels?.length} />
            <CreateChannel />
          </div>
          <ContactList contacts={channels} isChannel />
        </section>
      </div>

      {/* Bottom docked user profile bar */}
      <ProfileInfo />
    </aside>
  );
};

export default ContactContainer;

const SectionTitle = ({ text, count }) => (
  <div className="flex items-center gap-2">
    <h6 className="text-[11px] font-semibold uppercase tracking-[0.06em] text-zinc-500">
      {text}
    </h6>
    {typeof count === "number" && count > 0 && (
      <span className="text-[10px] font-mono text-zinc-500 bg-white/[0.05] px-1.5 py-0.5 rounded-md tabular-nums">
        {count}
      </span>
    )}
  </div>
);
