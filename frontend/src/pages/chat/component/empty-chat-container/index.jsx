import { MessageSquare, Hash, Paperclip } from "lucide-react";
import KasinaLogo from "@/components/kasina-logo";

const EmptyChatContainer = () => {
  return (
    <div className="flex-1 h-full bg-[#0B0C0E] flex flex-col items-center justify-center p-8 select-none">
      <div className="max-w-sm w-full text-center space-y-6">
        <div className="flex justify-center">
          <KasinaLogo size="lg" showWordmark={false} />
        </div>

        <div className="space-y-2">
          <h2 className="text-lg font-semibold tracking-[-0.02em] text-zinc-100">
            Select a conversation
          </h2>
          <p className="text-sm text-zinc-400 leading-relaxed">
            Pick an existing direct message or channel from the sidebar, or start
            a new thread to begin messaging.
          </p>
        </div>

        <div className="grid grid-cols-3 gap-2 pt-2">
          <div className="rounded-xl border border-white/[0.06] bg-[#111317] p-3 text-left space-y-1.5">
            <MessageSquare className="h-4 w-4 text-zinc-400" />
            <p className="text-[11px] font-medium text-zinc-300">Direct DMs</p>
          </div>
          <div className="rounded-xl border border-white/[0.06] bg-[#111317] p-3 text-left space-y-1.5">
            <Hash className="h-4 w-4 text-zinc-400" />
            <p className="text-[11px] font-medium text-zinc-300">Channels</p>
          </div>
          <div className="rounded-xl border border-white/[0.06] bg-[#111317] p-3 text-left space-y-1.5">
            <Paperclip className="h-4 w-4 text-zinc-400" />
            <p className="text-[11px] font-medium text-zinc-300">File sharing</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EmptyChatContainer;
