import { useEffect, useState, useRef } from "react";
import EmojiPicker from "emoji-picker-react";
import { ArrowUp, Paperclip, Smile } from "lucide-react";
import { toast } from "sonner";
import { useAppStore } from "@/store";
import { useSocket } from "@/context/SocketContext";
import { apiClient } from "@/lib/api-client";
import { UPLOAD_FILE_ROUTE } from "@/utils/constants";
import { cn, getDisplayName } from "@/lib/utils";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const MessageBar = () => {
  const [message, setMessage] = useState("");
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);
  const emojiRef = useRef(null);
  const inputRef = useRef(null);
  const fileInputRef = useRef(null);

  const {
    selectedChatType,
    selectedChatData,
    userInfo,
    setIsUploading,
    setFileUploadProgress,
  } = useAppStore();
  const socket = useSocket();

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (emojiRef.current && !emojiRef.current.contains(event.target)) {
        setEmojiPickerOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  useEffect(() => {
    inputRef.current?.focus();
  }, [selectedChatData?.id]);

  const handleAddEmoji = (emoji) => {
    setMessage((msg) => msg + emoji.emoji);
    inputRef.current?.focus();
  };

  const handleSendMessage = (e) => {
    if (e) e.preventDefault();
    const trimmed = message.trim();
    if (!trimmed) return;

    const payload = {
      sender: userInfo.id,
      content: trimmed,
      recipient: selectedChatData.id,
      messageType: "text",
      fileUrl: undefined,
    };

    if (selectedChatType === "contact") {
      socket?.sendMessage({
        ...payload,
        recipient: selectedChatData.id,
      });
    } else if (selectedChatType === "channel") {
      socket?.sendMessage({
        ...payload,
        channelId: selectedChatData.id,
        recipient: null,
      });
    }

    setMessage("");
    setEmojiPickerOpen(false);
  };

  const handleAttachmentClick = () => {
    fileInputRef.current?.click();
  };

  const handleAttachmentChange = async (event) => {
    try {
      const file = event.target.files?.[0];
      if (!file) return;

      const formData = new FormData();
      formData.append("file", file);

      setIsUploading(true);
      setFileUploadProgress(0);

      const response = await apiClient.post(UPLOAD_FILE_ROUTE, formData, {
        withCredentials: true,
        onUploadProgress: (data) => {
          if (data.total) {
            setFileUploadProgress(Math.round((100 * data.loaded) / data.total));
          }
        },
      });

      if (response.status === 200 && response.data) {
        const payload = {
          sender: userInfo.id,
          content: undefined,
          fileUrl: response.data.filePath,
          messageType: "file",
        };

        if (selectedChatType === "contact") {
          socket?.sendMessage({
            ...payload,
            recipient: selectedChatData.id,
          });
        } else if (selectedChatType === "channel") {
          socket?.sendMessage({
            ...payload,
            channelId: selectedChatData.id,
          });
        }
      }
    } catch (error) {
      console.error("File upload error:", error);
      toast.error(
        error?.response?.data?.message || "Failed to upload attachment."
      );
    } finally {
      setIsUploading(false);
      setFileUploadProgress(0);
      if (event?.target) {
        event.target.value = "";
      }
    }
  };

  const isChannel = selectedChatType === "channel";
  const targetName = getDisplayName(selectedChatData, isChannel);
  const placeholder = isChannel
    ? `Message #${targetName}`
    : `Message ${targetName}`;
  const canSend = message.trim().length > 0;

  return (
    <div className="shrink-0 px-4 sm:px-6 lg:px-8 pb-4 pt-2 bg-[#0B0C0E]">
      <form
        onSubmit={handleSendMessage}
        className="max-w-4xl mx-auto flex items-center gap-2 rounded-2xl border border-white/[0.09] bg-[#13151B] px-2.5 py-2 transition-colors focus-within:border-white/[0.18]"
      >
        <TooltipProvider delayDuration={250}>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={handleAttachmentClick}
                aria-label="Attach file"
                className="h-9 w-9 rounded-xl flex items-center justify-center text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.06] transition-colors shrink-0"
              >
                <Paperclip className="h-[18px] w-[18px]" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Attach file</TooltipContent>
          </Tooltip>
        </TooltipProvider>

        <input
          type="file"
          className="hidden"
          ref={fileInputRef}
          onChange={handleAttachmentChange}
        />

        <input
          ref={inputRef}
          type="text"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder={placeholder}
          aria-label={placeholder}
          className="flex-1 min-w-0 bg-transparent px-1.5 py-1.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none"
        />

        <div className="relative shrink-0" ref={emojiRef}>
          <TooltipProvider delayDuration={250}>
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={() => setEmojiPickerOpen((open) => !open)}
                  aria-label="Insert emoji"
                  className={cn(
                    "h-9 w-9 rounded-xl flex items-center justify-center transition-colors",
                    emojiPickerOpen
                      ? "bg-white/[0.08] text-zinc-100"
                      : "text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.06]"
                  )}
                >
                  <Smile className="h-[18px] w-[18px]" />
                </button>
              </TooltipTrigger>
              <TooltipContent>Emoji</TooltipContent>
            </Tooltip>
          </TooltipProvider>

          {emojiPickerOpen && (
            <div className="absolute bottom-12 right-0 z-40">
              <EmojiPicker
                theme="dark"
                open={emojiPickerOpen}
                onEmojiClick={handleAddEmoji}
                autoFocusSearch={false}
                lazyLoadEmojis
              />
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={!canSend}
          aria-label="Send message"
          className={cn(
            "h-9 w-9 rounded-xl flex items-center justify-center shrink-0 transition-all",
            canSend
              ? "bg-emerald-500 hover:bg-emerald-400 active:scale-95 text-zinc-950 shadow-sm"
              : "bg-white/[0.05] text-zinc-600 cursor-not-allowed"
          )}
        >
          <ArrowUp className="h-[18px] w-[18px] stroke-[2.25]" />
        </button>
      </form>
    </div>
  );
};

export default MessageBar;
