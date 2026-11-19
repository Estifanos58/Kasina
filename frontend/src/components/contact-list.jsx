import { Hash } from "lucide-react";
import { useAppStore } from "@/store";
import { cn, formatSidebarTime, getDisplayName } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";

function ContactList({ contacts, isChannel = false }) {
  const { selectedChatType, selectedChatData, selectConversation } =
    useAppStore();

  const handleClick = (contact) => {
    const nextType = isChannel ? "channel" : "contact";
    selectConversation(nextType, contact);
  };

  if (!contacts || contacts.length === 0) {
    return (
      <div className="px-4 py-3">
        <p className="text-xs text-zinc-500">
          {isChannel
            ? "No channels yet. Create one to start a group thread."
            : "No direct messages yet. Search above to start chatting."}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-0.5 px-2">
      {contacts.map((contact, index) => {
        const expectedType = isChannel ? "channel" : "contact";
        const isActive =
          selectedChatType === expectedType &&
          selectedChatData?.id === contact?.id;
        const displayName = getDisplayName(contact, isChannel);
        const timeLabel = !isChannel
          ? formatSidebarTime(contact?.lastMessageTime)
          : "";
        const memberCount =
          isChannel && Array.isArray(contact?.members)
            ? contact.members.length
            : null;

        return (
          <button
            type="button"
            key={contact?.id || index}
            onClick={() => handleClick(contact)}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "group relative w-full flex items-center gap-3 px-2.5 py-2 rounded-xl text-left transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500/60",
              isActive
                ? "bg-white/[0.08] text-zinc-50"
                : "text-zinc-300 hover:bg-white/[0.04] hover:text-zinc-100"
            )}
          >
            {isActive && (
              <span className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-r-full bg-emerald-500" />
            )}

            {isChannel ? (
              <div
                className={cn(
                  "h-9 w-9 rounded-xl flex items-center justify-center shrink-0 border transition-colors",
                  isActive
                    ? "bg-emerald-500/15 border-emerald-500/30 text-emerald-300"
                    : "bg-white/[0.04] border-white/[0.07] text-zinc-400 group-hover:text-zinc-200"
                )}
              >
                <Hash className="h-4 w-4" />
              </div>
            ) : (
              <UserAvatar user={contact} size="sm" />
            )}

            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <span
                  className={cn(
                    "text-[13.5px] truncate leading-tight",
                    isActive ? "font-semibold text-zinc-50" : "font-medium"
                  )}
                >
                  {displayName}
                </span>
                {timeLabel && (
                  <span className="text-[11px] font-mono text-zinc-500 tabular-nums shrink-0">
                    {timeLabel}
                  </span>
                )}
              </div>
              {!isChannel && contact?.email && displayName !== contact.email && (
                <p className="text-[11.5px] text-zinc-500 truncate mt-0.5 leading-tight">
                  {contact.email}
                </p>
              )}
              {isChannel && memberCount !== null && (
                <p className="text-[11.5px] text-zinc-500 truncate mt-0.5 leading-tight">
                  {memberCount} {memberCount === 1 ? "member" : "members"}
                </p>
              )}
            </div>
          </button>
        );
      })}
    </div>
  );
}

export default ContactList;
