import { useCallback, useEffect, useRef, useState } from "react";
import { AlertCircle, RefreshCw, Search, X } from "lucide-react";
import { apiClient, extractApiErrorMessage } from "@/lib/api-client";
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
  const [searchError, setSearchError] = useState(null);
  const searchAbortRef = useRef(null);
  const searchDebounceRef = useRef(null);

  const {
    setDirectMessagesContacts,
    directMessagesContacts,
    contactsLoading,
    contactsError,
    setContactsLoading,
    setContactsError,
    channels,
    channelsLoading,
    channelsError,
    setChannels,
    setChannelsLoading,
    setChannelsError,
    selectConversation,
  } = useAppStore();

  const socket = useSocket();
  const connectionStatus = socket?.connectionStatus || "disconnected";
  const isConnected = connectionStatus === "connected";

  const loadContacts = useCallback(
    async (signal) => {
      setContactsLoading(true);
      setContactsError(null);
      try {
        const response = await apiClient.get(GET_DM_CONTACTS_ROUTES, { signal });
        if (!signal?.aborted && Array.isArray(response.data)) {
          setDirectMessagesContacts(response.data);
        }
      } catch (err) {
        if (signal?.aborted || err?.code === "ERR_CANCELED") return;
        setContactsError(
          extractApiErrorMessage(err, "Unable to load direct messages.")
        );
      } finally {
        if (!signal?.aborted) setContactsLoading(false);
      }
    },
    [setContactsLoading, setContactsError, setDirectMessagesContacts]
  );

  const loadChannels = useCallback(
    async (signal) => {
      setChannelsLoading(true);
      setChannelsError(null);
      try {
        const response = await apiClient.get(GET_USER_CHANNELS_ROUTE, { signal });
        if (!signal?.aborted && Array.isArray(response.data)) {
          setChannels(response.data);
        }
      } catch (err) {
        if (signal?.aborted || err?.code === "ERR_CANCELED") return;
        setChannelsError(
          extractApiErrorMessage(err, "Unable to load channels.")
        );
      } finally {
        if (!signal?.aborted) setChannelsLoading(false);
      }
    },
    [setChannelsLoading, setChannelsError, setChannels]
  );

  useEffect(() => {
    const controller = new AbortController();
    loadContacts(controller.signal);
    loadChannels(controller.signal);
    return () => controller.abort();
  }, [loadContacts, loadChannels, socket?.reconnectEpoch]);

  useEffect(() => {
    return () => {
      if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
      if (searchAbortRef.current) searchAbortRef.current.abort();
    };
  }, []);

  const searchContacts = (searchTerm) => {
    setSearchQuery(searchTerm);
    setSearchError(null);

    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    if (searchAbortRef.current) searchAbortRef.current.abort();

    const trimmed = searchTerm.trim();
    if (!trimmed) {
      setSearchedContacts([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    searchDebounceRef.current = setTimeout(async () => {
      const controller = new AbortController();
      searchAbortRef.current = controller;
      try {
        const response = await apiClient.post(
          SEARCH_CONTACTS_ROUTES,
          { searchTerm: trimmed },
          { signal: controller.signal }
        );
        if (!controller.signal.aborted) {
          setSearchedContacts(Array.isArray(response.data) ? response.data : []);
        }
      } catch (error) {
        if (controller.signal.aborted || error?.code === "ERR_CANCELED") return;
        setSearchError(extractApiErrorMessage(error, "Search failed."));
        setSearchedContacts([]);
      } finally {
        if (!controller.signal.aborted) {
          setIsSearching(false);
        }
      }
    }, 220);
  };

  const selectNewContact = (contact) => {
    selectConversation("contact", contact);
    setSearchQuery("");
    setSearchedContacts([]);
    setSearchError(null);
  };

  const clearSearch = () => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    if (searchAbortRef.current) searchAbortRef.current.abort();
    setSearchQuery("");
    setSearchedContacts([]);
    setSearchError(null);
    setIsSearching(false);
  };

  const statusLabel = isConnected
    ? "Online"
    : connectionStatus === "reconnecting" || connectionStatus === "connecting"
      ? "Syncing"
      : "Offline";

  const statusTooltip = isConnected
    ? "Real-time connection active"
    : connectionStatus === "reconnecting"
      ? "Reconnecting to messaging server..."
      : connectionStatus === "connecting"
        ? "Connecting to messaging server..."
        : "Disconnected from messaging server";

  return (
    <aside
      aria-label="Conversations sidebar"
      className="flex flex-col w-full h-full bg-[#111317] border-r border-white/[0.07]"
    >
      {/* Top workspace header */}
      <div className="h-16 px-4 border-b border-white/[0.06] flex items-center justify-between shrink-0">
        <KasinaLogo size="sm" />
        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger asChild>
              <div
                role="status"
                aria-label={statusTooltip}
                className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-white/[0.04] border border-white/[0.06] text-[11px] font-medium text-zinc-400 cursor-default"
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isConnected
                      ? "bg-emerald-500"
                      : connectionStatus === "disconnected"
                        ? "bg-zinc-500"
                        : "bg-amber-500 animate-pulse"
                  }`}
                />
                <span>{statusLabel}</span>
              </div>
            </TooltipTrigger>
            <TooltipContent>{statusTooltip}</TooltipContent>
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
            onKeyDown={(e) => {
              if (e.key === "Escape") clearSearch();
            }}
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
            ) : searchError ? (
              <div className="px-3.5 py-4 text-xs text-red-400 text-center">
                {searchError}
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
      <nav
        aria-label="Direct messages and channels"
        className="flex-1 overflow-y-auto py-1 space-y-5"
      >
        <section>
          <div className="flex items-center justify-between px-4 mb-1.5">
            <SectionTitle
              text="Direct messages"
              count={directMessagesContacts?.length}
            />
            <NewDm />
          </div>
          {contactsLoading && directMessagesContacts.length === 0 ? (
            <SidebarSkeleton />
          ) : contactsError ? (
            <SidebarError
              message={contactsError}
              onRetry={() => loadContacts()}
            />
          ) : (
            <ContactList contacts={directMessagesContacts} />
          )}
        </section>

        <section>
          <div className="flex items-center justify-between px-4 mb-1.5">
            <SectionTitle text="Channels" count={channels?.length} />
            <CreateChannel />
          </div>
          {channelsLoading && channels.length === 0 ? (
            <SidebarSkeleton />
          ) : channelsError ? (
            <SidebarError
              message={channelsError}
              onRetry={() => loadChannels()}
            />
          ) : (
            <ContactList contacts={channels} isChannel />
          )}
        </section>
      </nav>

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

const SidebarSkeleton = () => (
  <div className="space-y-1.5 px-3 py-1" aria-label="Loading">
    {[0, 1, 2].map((i) => (
      <div
        key={i}
        className="flex items-center gap-3 px-2 py-2 rounded-xl animate-pulse"
      >
        <div className="h-9 w-9 rounded-full bg-white/[0.05] shrink-0" />
        <div className="flex-1 space-y-1.5">
          <div className="h-3 w-28 rounded bg-white/[0.06]" />
          <div className="h-2.5 w-20 rounded bg-white/[0.04]" />
        </div>
      </div>
    ))}
  </div>
);

const SidebarError = ({ message, onRetry }) => (
  <div className="mx-3 px-3 py-2.5 rounded-xl border border-red-500/20 bg-red-500/10 text-xs text-red-200 flex items-center justify-between gap-2">
    <div className="flex items-center gap-1.5 min-w-0">
      <AlertCircle className="h-3.5 w-3.5 text-red-400 shrink-0" />
      <span className="truncate">{message}</span>
    </div>
    <button
      type="button"
      onClick={onRetry}
      className="inline-flex items-center gap-1 text-red-200 hover:text-white font-medium shrink-0"
    >
      <RefreshCw className="h-3 w-3" />
      Retry
    </button>
  </div>
);
