import { useEffect, useRef, useState } from "react";
import moment from "moment";
import { Download, FileText, Hash, X, ZoomIn } from "lucide-react";
import { toast } from "sonner";
import { useAppStore } from "@/store";
import { apiClient } from "@/lib/api-client";
import {
  GET_ALL_MESSAGES_ROUTE,
  GET_CHANNEL_MESSAGES,
  HOST,
} from "@/utils/constants";
import { cn, formatDateDivider, getDisplayName } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";

const MessageContainer = () => {
  const {
    selectedChatType,
    selectedChatData,
    selectedChatMessages,
    userInfo,
    setSelectedChatMessages,
    setFileDownloadProgress,
    setIsDownloading,
  } = useAppStore();

  const scrollRef = useRef(null);
  const [showImage, setShowImage] = useState(false);
  const [imageUrl, setImageUrl] = useState(null);

  useEffect(() => {
    const getMessages = async () => {
      try {
        const response = await apiClient.post(
          GET_ALL_MESSAGES_ROUTE,
          { id: selectedChatData.id },
          { withCredentials: true }
        );
        if (response.data) {
          setSelectedChatMessages(response.data);
        }
      } catch (error) {
        console.error("Failed to fetch DM messages:", error);
      }
    };

    const getChannelMessages = async () => {
      try {
        const response = await apiClient.get(
          `${GET_CHANNEL_MESSAGES}/${selectedChatData.id}`,
          { withCredentials: true }
        );
        if (response.data) {
          setSelectedChatMessages(response.data);
        }
      } catch (error) {
        console.error("Failed to fetch channel messages:", error);
      }
    };

    if (selectedChatData?.id) {
      if (selectedChatType === "contact") getMessages();
      else if (selectedChatType === "channel") getChannelMessages();
    }
  }, [selectedChatType, selectedChatData, setSelectedChatMessages]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [selectedChatMessages]);

  useEffect(() => {
    if (!showImage) return;
    const onKeyDown = (e) => {
      if (e.key === "Escape") {
        setShowImage(false);
        setImageUrl(null);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [showImage]);

  const getFileUrl = (url) => {
    if (!url) return "";
    if (url.startsWith("http://") || url.startsWith("https://")) return url;
    const cleanHost = HOST.endsWith("/") ? HOST.slice(0, -1) : HOST;
    const cleanPath = url.startsWith("/") ? url : `/${url}`;
    return `${cleanHost}${cleanPath}`;
  };

  const checkImage = (filePath) => {
    if (!filePath) return false;
    return /\.(jpg|jpeg|png|gif|bmp|tiff|tif|webp|svg|ico|heic|heif)$/i.test(
      filePath
    );
  };

  const getFileName = (filePath) => {
    if (!filePath) return "Attachment";
    const raw = filePath.split("/").pop() || "Attachment";
    try {
      return decodeURIComponent(raw);
    } catch {
      return raw;
    }
  };

  const downloadFile = async (file) => {
    try {
      setIsDownloading(true);
      setFileDownloadProgress(0);
      const url = getFileUrl(file);
      const response = await apiClient.get(url, {
        responseType: "blob",
        onDownloadProgress: (progressEvent) => {
          const { loaded, total } = progressEvent;
          if (total) {
            const percent = Math.floor((loaded * 100) / total);
            setFileDownloadProgress(percent);
          }
        },
      });
      const urlBlob = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = urlBlob;
      link.setAttribute("download", getFileName(file));
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(urlBlob);
    } catch (error) {
      console.error("Error downloading file:", error);
      toast.error("Failed to download file.");
    } finally {
      setIsDownloading(false);
      setFileDownloadProgress(0);
    }
  };

  const renderFileAttachment = (fileUrl, isOwn) => {
    if (checkImage(fileUrl)) {
      return (
        <button
          type="button"
          onClick={() => {
            setShowImage(true);
            setImageUrl(fileUrl);
          }}
          className="group relative block overflow-hidden rounded-2xl border border-white/[0.09] bg-[#14161B] focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
        >
          <img
            src={getFileUrl(fileUrl)}
            alt={getFileName(fileUrl)}
            loading="lazy"
            className="max-w-[280px] sm:max-w-[320px] max-h-[320px] object-cover block"
          />
          <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-colors flex items-center justify-center">
            <span className="opacity-0 group-hover:opacity-100 transition-opacity h-9 w-9 rounded-full bg-black/65 backdrop-blur-sm text-white flex items-center justify-center">
              <ZoomIn className="h-4 w-4" />
            </span>
          </div>
        </button>
      );
    }

    const fileName = getFileName(fileUrl);
    const ext = fileName.includes(".")
      ? fileName.split(".").pop().toUpperCase().slice(0, 5)
      : "FILE";

    return (
      <div
        className={cn(
          "flex items-center gap-3 rounded-2xl border px-3.5 py-2.5 max-w-[300px] sm:max-w-[340px]",
          isOwn
            ? "bg-[#0F5146] border-emerald-400/20 text-zinc-50"
            : "bg-[#161920] border-white/[0.08] text-zinc-100"
        )}
      >
        <div
          className={cn(
            "h-10 w-10 rounded-xl flex items-center justify-center shrink-0",
            isOwn ? "bg-black/20 text-emerald-200" : "bg-white/[0.05] text-zinc-300"
          )}
        >
          <FileText className="h-4 w-4" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-[13px] font-medium truncate leading-snug">
            {fileName}
          </p>
          <p
            className={cn(
              "text-[10.5px] font-mono mt-0.5",
              isOwn ? "text-emerald-200/75" : "text-zinc-500"
            )}
          >
            {ext}
          </p>
        </div>
        <button
          type="button"
          onClick={() => downloadFile(fileUrl)}
          aria-label={`Download ${fileName}`}
          className={cn(
            "h-8 w-8 rounded-lg flex items-center justify-center shrink-0 transition-colors",
            isOwn
              ? "bg-black/20 hover:bg-black/35 text-emerald-100"
              : "bg-white/[0.05] hover:bg-white/[0.1] text-zinc-300"
          )}
        >
          <Download className="h-4 w-4" />
        </button>
      </div>
    );
  };

  const renderMessageRow = (message, index) => {
    const prevMessage = index > 0 ? selectedChatMessages[index - 1] : null;
    const ts = message.timestamps || message.timestamp;
    const prevTs = prevMessage?.timestamps || prevMessage?.timestamp;

    const currentDate = ts ? moment(ts).format("YYYY-MM-DD") : "";
    const prevDate = prevTs ? moment(prevTs).format("YYYY-MM-DD") : "";
    const showDate = !prevMessage || currentDate !== prevDate;

    const isSameSenderAsPrev =
      !showDate &&
      prevMessage &&
      prevMessage.sender === message.sender &&
      Math.abs(moment(ts).diff(moment(prevTs), "minutes")) < 5;

    const isOwn = message.sender === userInfo?.id;
    const isChannel = selectedChatType === "channel";
    const isFile = message.messageType === "file" && Boolean(message.fileUrl);

    return (
      <div key={message.id || index}>
        {showDate && ts && (
          <div className="flex items-center gap-3 my-6 select-none">
            <div className="h-px flex-1 bg-white/[0.06]" />
            <span className="text-[11px] font-medium text-zinc-500 px-2">
              {formatDateDivider(ts)}
            </span>
            <div className="h-px flex-1 bg-white/[0.06]" />
          </div>
        )}

        <div
          className={cn(
            "flex w-full",
            isOwn ? "justify-end" : "justify-start",
            isSameSenderAsPrev ? "mt-1" : "mt-4"
          )}
        >
          <div
            className={cn(
              "flex gap-2.5 max-w-[82%] sm:max-w-[70%] lg:max-w-[62%]",
              isOwn ? "flex-row-reverse" : "flex-row"
            )}
          >
            {/* Channel incoming avatar column */}
            {isChannel && !isOwn && (
              <div className="w-8 shrink-0 flex flex-col justify-start pt-0.5">
                {!isSameSenderAsPrev && (
                  <UserAvatar
                    image={message.image}
                    name={message.senderName}
                    color={message.color}
                    size="xs"
                  />
                )}
              </div>
            )}

            <div
              className={cn(
                "flex flex-col min-w-0",
                isOwn ? "items-end" : "items-start"
              )}
            >
              {/* Channel sender name on first message of group */}
              {isChannel && !isOwn && !isSameSenderAsPrev && (
                <div className="flex items-baseline gap-2 mb-1 px-0.5">
                  <span className="text-xs font-medium text-zinc-300">
                    {message.senderName || "Member"}
                  </span>
                  {ts && (
                    <span className="text-[10.5px] font-mono text-zinc-500 tabular-nums">
                      {moment(ts).format("HH:mm")}
                    </span>
                  )}
                </div>
              )}

              {/* Message payload: file OR text */}
              {isFile ? (
                renderFileAttachment(message.fileUrl, isOwn)
              ) : (
                <div
                  className={cn(
                    "px-3.5 py-2 text-[14px] leading-relaxed break-words whitespace-pre-wrap",
                    isOwn
                      ? "bg-[#0F5146] text-zinc-50 border border-emerald-400/20 rounded-2xl rounded-tr-sm"
                      : "bg-[#161920] text-zinc-100 border border-white/[0.07] rounded-2xl rounded-tl-sm",
                    isSameSenderAsPrev && isOwn && "rounded-tr-2xl",
                    isSameSenderAsPrev && !isOwn && "rounded-tl-2xl"
                  )}
                >
                  {message.content}
                </div>
              )}

              {/* Subtle timestamp for DMs and own channel messages */}
              {ts && (!isChannel || isOwn || isSameSenderAsPrev) && (
                <span className="text-[10.5px] font-mono text-zinc-600 tabular-nums mt-1 px-1">
                  {moment(ts).format("HH:mm")}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  };

  const isChannel = selectedChatType === "channel";
  const chatTitle = getDisplayName(selectedChatData, isChannel);

  return (
    <div className="flex-1 overflow-y-auto px-4 sm:px-6 lg:px-8 py-6">
      <div className="max-w-4xl mx-auto">
        {/* Conversation beginning banner */}
        <div className="pb-6 mb-2 border-b border-white/[0.05] flex flex-col items-start gap-2.5">
          {isChannel ? (
            <div className="h-12 w-12 rounded-2xl bg-emerald-500/12 border border-emerald-500/25 text-emerald-300 flex items-center justify-center">
              <Hash className="h-5 w-5" />
            </div>
          ) : (
            <UserAvatar user={selectedChatData} size="lg" />
          )}
          <div>
            <h3 className="text-base font-semibold tracking-[-0.015em] text-zinc-100">
              {isChannel ? `# ${chatTitle}` : chatTitle}
            </h3>
            <p className="text-xs text-zinc-500 mt-0.5">
              {isChannel
                ? `This is the start of the #${chatTitle} channel.`
                : `This is the beginning of your direct message history with ${chatTitle}.`}
            </p>
          </div>
        </div>

        {/* Message stream */}
        {selectedChatMessages.length > 0 && (
          <div className="pb-2">
            {selectedChatMessages.map((msg, idx) => renderMessageRow(msg, idx))}
          </div>
        )}

        <div ref={scrollRef} />
      </div>

      {/* Image lightbox */}
      {showImage && imageUrl && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Image preview"
          onClick={() => {
            setShowImage(false);
            setImageUrl(null);
          }}
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 sm:p-8"
        >
          <div
            className="fixed top-4 right-4 sm:top-6 sm:right-6 flex items-center gap-2 z-10"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => downloadFile(imageUrl)}
              aria-label="Download image"
              className="h-9 px-3 rounded-xl bg-[#181B22]/90 hover:bg-[#232732] border border-white/[0.1] text-zinc-200 text-xs font-medium inline-flex items-center gap-1.5 transition-colors"
            >
              <Download className="h-3.5 w-3.5" />
              <span>Download</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setShowImage(false);
                setImageUrl(null);
              }}
              aria-label="Close preview"
              className="h-9 w-9 rounded-xl bg-[#181B22]/90 hover:bg-[#232732] border border-white/[0.1] text-zinc-200 flex items-center justify-center transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <img
            src={getFileUrl(imageUrl)}
            alt={getFileName(imageUrl)}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[84vh] max-w-[90vw] object-contain rounded-xl border border-white/[0.08] shadow-2xl"
          />
        </div>
      )}
    </div>
  );
};

export default MessageContainer;
