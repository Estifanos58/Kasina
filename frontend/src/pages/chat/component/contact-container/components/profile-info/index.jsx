import { useNavigate } from "react-router-dom";
import { LogOut, Settings } from "lucide-react";
import { toast } from "sonner";
import { useAppStore } from "@/store";
import { useSocket } from "@/context/SocketContext";
import { LOGOUT_ROUTE } from "@/utils/constants";
import { apiClient } from "@/lib/api-client";
import { getDisplayName } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const ProfileInfo = () => {
  const { userInfo, setUserInfo, closedChat } = useAppStore();
  const socket = useSocket();
  const navigate = useNavigate();

  const logOut = async () => {
    try {
      const response = await apiClient.post(
        LOGOUT_ROUTE,
        { userId: userInfo?.id },
        { withCredentials: true }
      );
      if (response.status === 200) {
        closedChat();
        setUserInfo(null);
        navigate("/auth");
      }
    } catch (err) {
      console.error("Logout error:", err);
      toast.error("Failed to sign out. Please try again.");
    }
  };

  const displayName = getDisplayName(userInfo);

  return (
    <div className="h-16 px-3.5 border-t border-white/[0.06] bg-[#0F1115] flex items-center justify-between gap-2 shrink-0">
      <button
        type="button"
        onClick={() => navigate("/profile")}
        className="flex items-center gap-2.5 min-w-0 flex-1 rounded-xl p-1 -ml-1 text-left hover:bg-white/[0.04] transition-colors"
      >
        <UserAvatar
          user={userInfo}
          size="sm"
          status={socket?.isConnected ? "online" : "offline"}
        />
        <div className="min-w-0 flex-1">
          <p className="text-[13px] font-medium text-zinc-100 truncate leading-tight">
            {displayName}
          </p>
          <p className="text-[11px] text-zinc-500 truncate mt-0.5 leading-tight">
            {userInfo?.email}
          </p>
        </div>
      </button>

      <div className="flex items-center gap-1 shrink-0">
        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={() => navigate("/profile")}
                aria-label="Edit profile"
                className="h-8 w-8 rounded-lg flex items-center justify-center text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.06] transition-colors"
              >
                <Settings className="h-4 w-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Profile settings</TooltipContent>
          </Tooltip>
        </TooltipProvider>

        <TooltipProvider delayDuration={200}>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={logOut}
                aria-label="Sign out"
                className="h-8 w-8 rounded-lg flex items-center justify-center text-zinc-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              >
                <LogOut className="h-4 w-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Sign out</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
    </div>
  );
};

export default ProfileInfo;
