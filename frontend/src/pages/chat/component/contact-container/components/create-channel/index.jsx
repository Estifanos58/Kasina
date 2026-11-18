import { useEffect, useState } from "react";
import { Hash, Loader2, Plus } from "lucide-react";
import { toast } from "sonner";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { apiClient } from "@/lib/api-client";
import { CREATE_CHANNEL_ROUTE, GET_ALL_CONTACTS } from "@/utils/constants";
import { useAppStore } from "@/store";
import { getDisplayName } from "@/lib/utils";
import MultipleSelector from "@/components/ui/multipleselect";

const CreateChannel = () => {
  const { addChannel, setSelectedChatType, setSelectedChatData, setSelectedChatMessages } =
    useAppStore();
  const [newChannelModal, setNewChannelModal] = useState(false);
  const [allContacts, setAllContacts] = useState([]);
  const [selectedContacts, setSelectedContacts] = useState([]);
  const [channelName, setChannelName] = useState("");
  const [loading, setLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!newChannelModal) return;

    const getData = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await apiClient.get(GET_ALL_CONTACTS, {
          withCredentials: true,
        });

        const contactsData = (response.data || []).map((contact) => {
          const name = getDisplayName(contact);
          const label =
            name && name !== contact.email
              ? `${name} (${contact.email})`
              : contact.email;
          return {
            label,
            value: contact.id,
          };
        });

        setAllContacts(contactsData);
      } catch (err) {
        console.error("Failed to fetch contacts:", err);
        setError("Failed to load contacts.");
      } finally {
        setLoading(false);
      }
    };

    getData();
  }, [newChannelModal]);

  const handleCreateChannel = async (e) => {
    e.preventDefault();
    const trimmedName = channelName.trim();
    if (!trimmedName) {
      toast.error("Please enter a channel name.");
      return;
    }
    if (selectedContacts.length === 0) {
      toast.error("Select at least one member.");
      return;
    }

    try {
      setIsCreating(true);
      const response = await apiClient.post(
        CREATE_CHANNEL_ROUTE,
        {
          name: trimmedName,
          members: selectedContacts.map((contact) => contact.value),
        },
        { withCredentials: true }
      );
      if (response.status === 201 && response.data) {
        addChannel(response.data);
        setSelectedChatMessages([]);
        setSelectedChatType("channel");
        setSelectedChatData(response.data);
        setChannelName("");
        setSelectedContacts([]);
        setNewChannelModal(false);
        toast.success(`Channel #${trimmedName} created.`);
      }
    } catch (err) {
      const msg =
        err?.response?.data?.message || "Failed to create channel.";
      toast.error(msg);
    } finally {
      setIsCreating(false);
    }
  };

  const handleOpenChange = (open) => {
    setNewChannelModal(open);
    if (!open) {
      setChannelName("");
      setSelectedContacts([]);
      setError(null);
    }
  };

  return (
    <>
      <TooltipProvider delayDuration={200}>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={() => setNewChannelModal(true)}
              aria-label="Create new channel"
              className="h-6 w-6 rounded-md flex items-center justify-center text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.07] transition-colors"
            >
              <Plus className="h-4 w-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent>Create channel</TooltipContent>
        </Tooltip>
      </TooltipProvider>

      <Dialog open={newChannelModal} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-[460px]">
          <DialogHeader>
            <DialogTitle>Create a channel</DialogTitle>
            <DialogDescription>
              Channels keep group conversations organized around a topic or team.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateChannel} className="space-y-4">
            <div className="space-y-1.5">
              <label
                htmlFor="channel-name-input"
                className="block text-xs font-medium text-zinc-300"
              >
                Channel name
              </label>
              <div className="relative">
                <Hash className="h-4 w-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  id="channel-name-input"
                  type="text"
                  autoFocus
                  placeholder="design-systems"
                  value={channelName}
                  onChange={(e) => setChannelName(e.target.value)}
                  disabled={isCreating}
                  className="w-full h-10 rounded-xl bg-[#161920] border border-white/[0.09] pl-9 pr-3.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-zinc-300">
                Members
              </label>
              {loading ? (
                <div className="h-10 rounded-xl bg-[#161920] border border-white/[0.07] flex items-center gap-2 px-3.5 text-xs text-zinc-400">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Loading contacts...</span>
                </div>
              ) : error ? (
                <p className="text-xs text-red-400 py-2">{error}</p>
              ) : (
                <MultipleSelector
                  options={allContacts}
                  defaultOptions={allContacts}
                  placeholder="Add people to this channel..."
                  value={selectedContacts}
                  onChange={setSelectedContacts}
                  emptyIndicator={
                    <p className="py-4 text-center text-xs text-zinc-500">
                      No matching contacts found.
                    </p>
                  }
                />
              )}
            </div>

            <div className="pt-2 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => handleOpenChange(false)}
                className="h-9 px-3.5 rounded-xl text-sm font-medium text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.05] transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={
                  isCreating ||
                  !channelName.trim() ||
                  selectedContacts.length === 0
                }
                className="h-9 px-4 rounded-xl bg-zinc-100 hover:bg-white text-zinc-950 text-sm font-medium inline-flex items-center gap-2 transition-all disabled:opacity-45 disabled:pointer-events-none"
              >
                {isCreating ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span>Creating...</span>
                  </>
                ) : (
                  <span>Create channel</span>
                )}
              </button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateChannel;
