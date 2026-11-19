import {
  memo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import moment from "moment";
import {
  AlertCircle,
  ArrowDown,
  Check,
  Clock,
  Download,
  FileText,
  Hash,
  Loader2,
  RefreshCw,
  WifiOff,
  X,
  ZoomIn,
} from "lucide-react";
import { toast } from "sonner";
import { useAppStore } from "@/store";
import { useSocket } from "@/context/SocketContext";
import { apiClient, extractApiErrorMessage } from "@/lib/api-client";
import {
  GET_ALL_MESSAGES_ROUTE,
  GET_CHANNEL_MESSAGES,
  HOST,
} from "@/utils/constants";
import { cn, formatDateDivider, getDisplayName } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";
import { getConversationKey } from "@/store/slices/chat-slice";

const PAGE_SIZE = 50;
const BOTTOM_THRESHOLD_PX = 140;

const getFileUrl = (url) => {
  if (!url) return "";
  if (url.startsWith("http://") || url.startsWith("https://")) return url;
  const cleanPath = url.startsWith("/") ? url : `/${url}`;
  if (!HOST) return cleanPath;
  const cleanHost = HOST.endsWith("/") ? HOST.slice(0, -1) : HOST;
  return `${cleanHost}${cleanPath}`;
};

const checkImage = (filePath) => {
  if (!filePath) return false;
  return /\.(jpg|jpeg|png|gif|webp)$/i.test(filePath);
};

const getFileName = (filePath) => {
  if (!filePath) return "Attachment";
  const raw = filePath.split("/").pop() || "Attachment";
  // Strip server-generated UUID prefix (uuid_filename) for cleaner display
  const stripped = raw.replace(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/i,
    ""
  );
  try {
    return decodeURIComponent(stripped || raw);
  } catch {
    return stripped || raw;
  }
};

const MessageRow = memo(function MessageRow({
  message,
  prevMessage,
  isOwn,
  isChannel,
  onOpenImage,
  onDownloadFile,
  onRetryMessage,
}) {
  const [imageError, setImageError] = useState(false);

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

  const isFile = message.messageType === "file" && Boolean(message.fileUrl);
  const isImageFile = isFile && checkImage(message.fileUrl) && !imageError;
  const isPending = message.status === "pending";
  const isFailed = message.status === "failed";

  const renderFileAttachment = () => {
    if (isImageFile) {
      return (
        <button
          type="button"
          onClick={() => onOpenImage(message.fileUrl)}
          className="group relative block overflow-hidden rounded-2xl border border-white/[0.09] bg-[#14161B] focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
        >
          <img
            src={getFileUrl(message.fileUrl)}
            alt={getFileName(message.fileUrl)}
            loading="lazy"
            onError={() => setImageError(true)}
            className="max-w-[260px] sm:max-w-[320px] max-h-[320px] object-cover block"
          />
          <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-colors flex items-center justify-center">
            <span className="opacity-0 group-hover:opacity-100 transition-opacity h-9 w-9 rounded-full bg-black/65 backdrop-blur-sm text-white flex items-center justify-center">
              <ZoomIn className="h-4 w-4" />
            </span>
          </div>
        </button>
      );
    }

    const fileName = getFileName(message.fileUrl);
    const ext = fileName.includes(".")
      ? fileName.split(".").pop().toUpperCase().slice(0, 5)
      : "FILE";

    return (
      <div
        className={cn(
          "flex items-center gap-3 rounded-2xl border px-3.5 py-2.5 max-w-[280px] sm:max-w-[340px]",
          isOwn
            ? "bg-[#0F5146] border-emerald-400/20 text-zinc-50"
            : "bg-[#161920] border-white/[0.08] text-zinc-100"
        )}
      >
        <div
          className={cn(
            "h-10 w-10 rounded-xl flex items-center justify-center shrink-0",
            isOwn
              ? "bg-black/20 text-emerald-200"
              : "bg-white/[0.05] text-zinc-300"
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
          onClick={() => onDownloadFile(message.fileUrl)}
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

  return (
    <div>
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
            "flex gap-2.5 max-w-[84%] sm:max-w-[70%] lg:max-w-[62%]",
            isOwn ? "flex-row-reverse" : "flex-row"
          )}
        >
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

            <div className={cn(isPending && "opacity-75 transition-opacity")}>
              {isFile ? (
                renderFileAttachment()
              ) : (
                <div
                  className={cn(
                    "px-3.5 py-2 text-[14px] leading-relaxed break-words whitespace-pre-wrap",
                    isOwn
                      ? isFailed
                        ? "bg-red-950/60 text-red-100 border border-red-500/35 rounded-2xl rounded-tr-sm"
                        : "bg-[#0F5146] text-zinc-50 border border-emerald-400/20 rounded-2xl rounded-tr-sm"
                      : "bg-[#161920] text-zinc-100 border border-white/[0.07] rounded-2xl rounded-tl-sm",
                    isSameSenderAsPrev && isOwn && "rounded-tr-2xl",
                    isSameSenderAsPrev && !isOwn && "rounded-tl-2xl"
                  )}
                >
                  {message.content}
                </div>
              )}
            </div>

            <div className="flex items-center gap-1.5 mt-1 px-1">
              {ts && (!isChannel || isOwn || isSameSenderAsPrev) && (
                <span className="text-[10.5px] font-mono text-zinc-600 tabular-nums">
                  {moment(ts).format("HH:mm")}
                </span>
              )}

              {isOwn && isPending && (
                <span
                  className="inline-flex items-center gap-1 text-[10.5px] text-zinc-500"
                  aria-label="Sending message"
                >
                  <Clock className="h-3 w-3 animate-pulse" />
                  <span>Sending</span>
                </span>
              )}

              {isOwn && !isPending && !isFailed && (
                <Check
                  className="h-3 w-3 text-emerald-500/70"
                  aria-label="Sent"
                />
              )}

              {isOwn && isFailed && (
                <div className="inline-flex items-center gap-1.5 text-[11px] text-red-400">
                  <AlertCircle className="h-3 w-3 shrink-0" />
                  <span>{message.error || "Not delivered"}</span>
                  {message.clientMessageId && (
                    <button
                      type="button"
                      onClick={() => onRetryMessage(message.clientMessageId)}
                      className="inline-flex items-center gap-1 text-red-300 hover:text-red-100 underline underline-offset-2 font-medium ml-0.5"
                    >
                      <RefreshCw className="h-2.5 w-2.5" />
                      Retry
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
});

const MessageContainer = () => {
  const {
    selectedChatType,
    selectedChatData,
    activeConversationKey,
    selectedChatMessages,
    messagesLoading,
    messagesError,
    loadingOlderMessages,
    hasMoreMessages,
    nextCursor,
    pendingOutbox,
    userInfo,
    setConversationMessages,
    prependOlderMessages,
    setMessagesLoading,
    setMessagesError,
    setLoadingOlderMessages,
    setFileDownloadProgress,
    setIsDownloading,
  } = useAppStore();

  const socket = useSocket();
  const scrollContainerRef = useRef(null);
  const bottomSentinelRef = useRef(null);
  const previousScrollHeightRef = useRef(null);
  const lastConvKeyRef = useRef(null);
  const prevMessageCountRef = useRef(0);
  const isNearBottomRef = useRef(true);

  const [showImage, setShowImage] = useState(false);
  const [imageUrl, setImageUrl] = useState(null);
  const [showJumpToLatest, setShowJumpToLatest] = useState(false);
  const [unreadWhileScrolled, setUnreadWhileScrolled] = useState(0);

  const queuedCountForConversation = useMemo(() => {
    if (!activeConversationKey || !pendingOutbox) return 0;
    return Object.values(pendingOutbox).filter(
      (m) => m.conversationKey === activeConversationKey && m.status === "pending"
    ).length;
  }, [activeConversationKey, pendingOutbox]);

  const fetchConversationHistory = useCallback(
    async ({ signal, cursor = null, isOlder = false } = {}) => {
      if (!selectedChatData?.id || !selectedChatType) return;
      const convKey = getConversationKey(selectedChatType, selectedChatData.id);
      if (!convKey) return;

      if (isOlder) {
        setLoadingOlderMessages(convKey, true);
      } else {
        setMessagesLoading(convKey, true);
        setMessagesError(convKey, null);
      }

      try {
        let response;
        if (selectedChatType === "contact") {
          response = await apiClient.post(
            GET_ALL_MESSAGES_ROUTE,
            {
              id: selectedChatData.id,
              limit: PAGE_SIZE,
              paginated: true,
              ...(cursor ? { cursor } : {}),
            },
            { signal }
          );
        } else if (selectedChatType === "channel") {
          response = await apiClient.get(
            `${GET_CHANNEL_MESSAGES}/${selectedChatData.id}`,
            {
              params: {
                limit: PAGE_SIZE,
                paginated: true,
                ...(cursor ? { cursor } : {}),
              },
              signal,
            }
          );
        }

        if (signal?.aborted) return;

        const data = response?.data;
        const messages = Array.isArray(data) ? data : data?.messages || [];
        const pagination = Array.isArray(data)
          ? { hasMore: false, nextCursor: null }
          : { hasMore: Boolean(data?.hasMore), nextCursor: data?.nextCursor || null };

        if (isOlder) {
          prependOlderMessages(convKey, messages, pagination);
        } else {
          setConversationMessages(convKey, messages, pagination);
        }
      } catch (error) {
        if (signal?.aborted || error?.code === "ERR_CANCELED") {
          return;
        }
        const errMsg = extractApiErrorMessage(
          error,
          "Could not load conversation history."
        );
        if (isOlder) {
          setLoadingOlderMessages(convKey, false);
          if (errMsg) toast.error(errMsg);
        } else {
          setMessagesError(convKey, errMsg || "Failed to load messages.");
        }
      }
    },
    [
      selectedChatType,
      selectedChatData?.id,
      setConversationMessages,
      prependOlderMessages,
      setMessagesLoading,
      setMessagesError,
      setLoadingOlderMessages,
    ]
  );

  // Fetch conversation messages with AbortController to prevent async race conditions
  useEffect(() => {
    if (!selectedChatData?.id || !selectedChatType) return;
    const controller = new AbortController();
    fetchConversationHistory({ signal: controller.signal });
    return () => {
      controller.abort();
    };
  }, [
    selectedChatType,
    selectedChatData?.id,
    socket?.reconnectEpoch,
    fetchConversationHistory,
  ]);

  const handleLoadOlder = useCallback(() => {
    if (!hasMoreMessages || !nextCursor || loadingOlderMessages) return;
    const el = scrollContainerRef.current;
    if (el) {
      previousScrollHeightRef.current = el.scrollHeight;
    }
    fetchConversationHistory({ cursor: nextCursor, isOlder: true });
  }, [hasMoreMessages, nextCursor, loadingOlderMessages, fetchConversationHistory]);

  // Preserve scroll position when prepending older messages
  useLayoutEffect(() => {
    const el = scrollContainerRef.current;
    if (!el || previousScrollHeightRef.current === null) return;
    const delta = el.scrollHeight - previousScrollHeightRef.current;
    if (delta > 0) {
      el.scrollTop = el.scrollTop + delta;
    }
    previousScrollHeightRef.current = null;
  }, [selectedChatMessages]);

  // Smart auto-scroll: scroll on initial open, own send, or when already near bottom
  useEffect(() => {
    const el = scrollContainerRef.current;
    if (!el) return;

    const conversationChanged = lastConvKeyRef.current !== activeConversationKey;
    if (conversationChanged) {
      lastConvKeyRef.current = activeConversationKey;
      prevMessageCountRef.current = 0;
      isNearBottomRef.current = true;
      setShowJumpToLatest(false);
      setUnreadWhileScrolled(0);
    }

    const currentCount = selectedChatMessages.length;
    const addedCount = currentCount - prevMessageCountRef.current;
    const lastMessage = selectedChatMessages[currentCount - 1];
    const isLastOwn = lastMessage && lastMessage.sender === userInfo?.id;

    if (previousScrollHeightRef.current !== null) {
      prevMessageCountRef.current = currentCount;
      return;
    }

    if (conversationChanged || prevMessageCountRef.current === 0) {
      el.scrollTop = el.scrollHeight;
      setShowJumpToLatest(false);
      setUnreadWhileScrolled(0);
    } else if (addedCount > 0) {
      if (isNearBottomRef.current || isLastOwn) {
        bottomSentinelRef.current?.scrollIntoView({ behavior: "smooth" });
        setShowJumpToLatest(false);
        setUnreadWhileScrolled(0);
      } else {
        setShowJumpToLatest(true);
        setUnreadWhileScrolled((prev) => prev + addedCount);
      }
    }

    prevMessageCountRef.current = currentCount;
  }, [selectedChatMessages, activeConversationKey, userInfo?.id]);

  const handleScroll = useCallback(() => {
    const el = scrollContainerRef.current;
    if (!el) return;

    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const nearBottom = distanceFromBottom <= BOTTOM_THRESHOLD_PX;
    isNearBottomRef.current = nearBottom;

    if (nearBottom) {
      setShowJumpToLatest(false);
      setUnreadWhileScrolled(0);
    }

    // Trigger loading older messages when scrolling near the top
    if (
      el.scrollTop < 120 &&
      hasMoreMessages &&
      nextCursor &&
      !loadingOlderMessages &&
      !messagesLoading
    ) {
      handleLoadOlder();
    }
  }, [
    hasMoreMessages,
    nextCursor,
    loadingOlderMessages,
    messagesLoading,
    handleLoadOlder,
  ]);

  const scrollToLatest = useCallback(() => {
    bottomSentinelRef.current?.scrollIntoView({ behavior: "smooth" });
    setShowJumpToLatest(false);
    setUnreadWhileScrolled(0);
  }, []);

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

  const handleOpenImage = useCallback((url) => {
    setImageUrl(url);
    setShowImage(true);
  }, []);

  const downloadFile = useCallback(
    async (file) => {
      try {
        setIsDownloading(true);
        setFileDownloadProgress(0);
        const url = getFileUrl(file);
        const response = await apiClient.get(url, {
          responseType: "blob",
          onDownloadProgress: (progressEvent) => {
            const { loaded, total } = progressEvent;
            if (total) {
              setFileDownloadProgress(Math.floor((loaded * 100) / total));
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
        const errMsg = extractApiErrorMessage(
          error,
          "Failed to download attachment."
        );
        if (errMsg) toast.error(errMsg);
      } finally {
        setIsDownloading(false);
        setFileDownloadProgress(0);
      }
    },
    [setFileDownloadProgress, setIsDownloading]
  );

  const handleRetryMessage = useCallback(
    (clientMessageId) => {
      socket?.retryMessage(clientMessageId);
    },
    [socket]
  );

  const isChannel = selectedChatType === "channel";
  const chatTitle = getDisplayName(selectedChatData, isChannel);
  const showOfflineBanner =
    socket?.connectionStatus === "reconnecting" ||
    socket?.connectionStatus === "disconnected";

  return (
    <div className="relative flex-1 min-h-0 flex flex-col">
      {showOfflineBanner && (
        <div
          role="status"
          aria-live="polite"
          className="px-4 py-2 bg-amber-500/10 border-b border-amber-500/20 text-amber-200 text-xs flex items-center justify-center gap-2 shrink-0"
        >
          <WifiOff className="h-3.5 w-3.5 shrink-0" />
          <span>
            {socket?.connectionStatus === "reconnecting"
              ? "Reconnecting to messaging server..."
              : "You appear to be offline."}
            {queuedCountForConversation > 0 &&
              ` (${queuedCountForConversation} message${
                queuedCountForConversation === 1 ? "" : "s"
              } queued)`}
          </span>
        </div>
      )}

      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        role="log"
        aria-live="polite"
        aria-busy={messagesLoading}
        aria-label={`Messages in ${chatTitle}`}
        className="flex-1 overflow-y-auto px-4 sm:px-6 lg:px-8 py-6"
      >
        <div className="max-w-4xl mx-auto">
          {/* Load older messages control / conversation beginning banner */}
          {hasMoreMessages ? (
            <div className="flex justify-center pb-4">
              <button
                type="button"
                onClick={handleLoadOlder}
                disabled={loadingOlderMessages}
                className="inline-flex items-center gap-2 h-8 px-3.5 rounded-full bg-[#14171D] hover:bg-[#1B1F27] border border-white/[0.08] text-xs font-medium text-zinc-300 transition-colors disabled:opacity-60"
              >
                {loadingOlderMessages ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span>Loading earlier messages...</span>
                  </>
                ) : (
                  <span>Load earlier messages</span>
                )}
              </button>
            </div>
          ) : (
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
          )}

          {/* Initial loading skeleton */}
          {messagesLoading && selectedChatMessages.length === 0 && (
            <div className="space-y-4 py-6" aria-label="Loading messages">
              {[0, 1, 2, 3].map((idx) => (
                <div
                  key={idx}
                  className={cn(
                    "flex",
                    idx % 2 === 1 ? "justify-end" : "justify-start"
                  )}
                >
                  <div
                    className={cn(
                      "h-10 rounded-2xl animate-pulse",
                      idx % 2 === 1
                        ? "w-48 bg-emerald-500/10"
                        : "w-64 bg-white/[0.05]"
                    )}
                  />
                </div>
              ))}
            </div>
          )}

          {/* Inline error state with retry */}
          {messagesError && (
            <div
              role="alert"
              className="my-6 rounded-xl border border-red-500/25 bg-red-500/10 px-4 py-3 flex items-center justify-between gap-3 text-xs text-red-200"
            >
              <div className="flex items-center gap-2 min-w-0">
                <AlertCircle className="h-4 w-4 text-red-400 shrink-0" />
                <span className="truncate">{messagesError}</span>
              </div>
              <button
                type="button"
                onClick={() => fetchConversationHistory()}
                className="inline-flex items-center gap-1.5 h-7 px-2.5 rounded-lg bg-red-500/20 hover:bg-red-500/30 text-red-100 font-medium shrink-0 transition-colors"
              >
                <RefreshCw className="h-3 w-3" />
                Retry
              </button>
            </div>
          )}

          {/* Message stream */}
          {selectedChatMessages.length > 0 && (
            <div className="pb-2">
              {selectedChatMessages.map((msg, idx) => (
                <MessageRow
                  key={msg.id || msg.clientMessageId || idx}
                  message={msg}
                  prevMessage={idx > 0 ? selectedChatMessages[idx - 1] : null}
                  isOwn={msg.sender === userInfo?.id}
                  isChannel={isChannel}
                  onOpenImage={handleOpenImage}
                  onDownloadFile={downloadFile}
                  onRetryMessage={handleRetryMessage}
                />
              ))}
            </div>
          )}

          <div ref={bottomSentinelRef} />
        </div>
      </div>

      {/* Floating jump-to-latest pill when scrolled up */}
      {showJumpToLatest && (
        <button
          type="button"
          onClick={scrollToLatest}
          className="absolute bottom-4 left-1/2 -translate-x-1/2 z-20 inline-flex items-center gap-1.5 h-8 px-3.5 rounded-full bg-[#181B22]/95 hover:bg-[#222630] border border-white/[0.12] text-xs font-medium text-zinc-100 shadow-xl backdrop-blur-md transition-all"
        >
          <ArrowDown className="h-3.5 w-3.5 text-emerald-400" />
          <span>
            {unreadWhileScrolled > 0
              ? `${unreadWhileScrolled} new message${
                  unreadWhileScrolled === 1 ? "" : "s"
                }`
              : "Jump to latest"}
          </span>
        </button>
      )}

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
