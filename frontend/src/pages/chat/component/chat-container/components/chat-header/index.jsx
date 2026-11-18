import { ArrowLeft, Hash, X } from "lucide-react";
import { useAppStore } from "@/store";
import { getDisplayName } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const ChatHeader = () => {
  const { closedChat, selectedChatData, selectedChatType } = useAppStore();
  const isChannel = selectedChatType === "channel";
  const title = getDisplayName(selectedChatData, isChannel);

  const memberCount =
    isChannel && Array.isArray(selectedChatData?.members)
      ? selectedChatData.members.length
      : null;

  const subtitle = isChannel
    ? memberCount !== null
      ? `${memberCount} ${memberCount === 1 ? "member" : "members"}`
      : "Channel"
    : selectedChatData?.email || "";

  return (
    <header className="h-16 px-4 sm:px-6 border-b border-white/[0.06] bg-[#0D0F13]/90 backdrop-blur-md flex items-center justify-between gap-4 shrink-0">
      <div className="flex items-center gap-3 min-w-0">
        {/* Mobile back button */}
        <button
          type="button"
          onClick={closedChat}
          aria-label="Back to conversations"
          className="md:hidden h-8 w-8 -ml-1 rounded-lg flex items-center justify-center text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.06] transition-colors shrink-0"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>

        {isChannel ? (
          <div className="h-9 w-9 rounded-xl bg-emerald-500/12 border border-emerald-500/25 text-emerald-300 flex items-center justify-center shrink-0">
            <Hash className="h-4 w-4" />
          </div>
        ) : (
          <UserAvatar user={selectedChatData} size="sm" />
        )}

        <div className="min-w-0">
          <h2 className="text-[14.5px] font-semibold text-zinc-100 truncate leading-tight">
            {title}
          </h2>
          {subtitle && subtitle !== title && (
            <p className="text-xs text-zinc-500 truncate mt-0.5 leading-tight">
              {subtitle}
            </p>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1 shrink-0">
        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={closedChat}
                aria-label="Close conversation"
                className="hidden md:flex h-8 w-8 rounded-lg items-center justify-center text-zinc-500 hover:text-zinc-100 hover:bg-white/[0.06] transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Close conversation</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
    </header>
  );
};

export default ChatHeader;
