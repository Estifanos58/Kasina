import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowDownToLine, ArrowUpFromLine } from "lucide-react";
import { useAppStore } from "@/store";
import ContactContainer from "./component/contact-container";
import EmptyChatContainer from "./component/empty-chat-container";
import ChatContainer from "./component/chat-container";

const Chat = () => {
  const {
    userInfo,
    selectedChatType,
    isUploading,
    isDownloading,
    fileUploadProgress,
    fileDownloadProgress,
  } = useAppStore();
  const navigate = useNavigate();

  useEffect(() => {
    if (userInfo && !userInfo.profileSetup) {
      navigate("/profile", { replace: true });
    }
  }, [userInfo, navigate]);

  return (
    <div className="flex h-dvh w-screen overflow-hidden bg-[#0B0C0E] text-zinc-100">
      {/* Sidebar: hidden on mobile when a conversation is active */}
      <div
        className={`${
          selectedChatType ? "hidden md:flex" : "flex"
        } w-full md:w-[300px] lg:w-[320px] xl:w-[340px] shrink-0 h-full`}
      >
        <ContactContainer />
      </div>

      {/* Main conversation pane */}
      <div
        className={`${
          selectedChatType ? "flex" : "hidden md:flex"
        } flex-1 min-w-0 h-full`}
      >
        {selectedChatType ? <ChatContainer /> : <EmptyChatContainer />}
      </div>

      {/* Non-blocking floating transfer indicator */}
      {(isUploading || isDownloading) && (
        <div className="fixed bottom-5 right-5 z-50 w-72 rounded-xl border border-white/[0.1] bg-[#14161B]/95 backdrop-blur-md p-3.5 shadow-2xl space-y-2.5">
          <div className="flex items-center justify-between text-xs">
            <div className="flex items-center gap-2 font-medium text-zinc-200">
              {isUploading ? (
                <ArrowUpFromLine className="h-3.5 w-3.5 text-emerald-400" />
              ) : (
                <ArrowDownToLine className="h-3.5 w-3.5 text-emerald-400" />
              )}
              <span>{isUploading ? "Uploading file..." : "Downloading file..."}</span>
            </div>
            <span className="font-mono text-zinc-400 tabular-nums">
              {isUploading ? fileUploadProgress : fileDownloadProgress}%
            </span>
          </div>
          <div className="h-1.5 w-full rounded-full bg-white/[0.07] overflow-hidden">
            <div
              className="h-full rounded-full bg-emerald-500 transition-all duration-150"
              style={{
                width: `${
                  isUploading ? fileUploadProgress : fileDownloadProgress
                }%`,
              }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default Chat;
