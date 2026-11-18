import { useState } from "react";
import { Plus, Search, Users } from "lucide-react";
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
import { ScrollArea } from "@/components/ui/scroll-area";
import { apiClient } from "@/lib/api-client";
import { SEARCH_CONTACTS_ROUTES } from "@/utils/constants";
import { getDisplayName } from "@/lib/utils";
import { useAppStore } from "@/store";
import UserAvatar from "@/components/user-avatar";

const NewDm = () => {
  const { setSelectedChatType, setSelectedChatData, setSelectedChatMessages } =
    useAppStore();
  const [openNewContact, setOpenNewContact] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchedContacts, setSearchedContacts] = useState([]);
  const [isSearching, setIsSearching] = useState(false);

  const searchContacts = async (query) => {
    setSearchTerm(query);
    const trimmed = query.trim();
    if (!trimmed) {
      setSearchedContacts([]);
      setIsSearching(false);
      return;
    }

    try {
      setIsSearching(true);
      const response = await apiClient.post(
        SEARCH_CONTACTS_ROUTES,
        { searchTerm: trimmed },
        { withCredentials: true }
      );

      if (response.status === 200 && Array.isArray(response.data)) {
        setSearchedContacts(response.data);
      } else {
        setSearchedContacts([]);
      }
    } catch (error) {
      console.error("Error searching contacts:", error);
      setSearchedContacts([]);
    } finally {
      setIsSearching(false);
    }
  };

  const selectNewContact = (contact) => {
    setOpenNewContact(false);
    setSelectedChatMessages([]);
    setSelectedChatType("contact");
    setSelectedChatData(contact);
    setSearchTerm("");
    setSearchedContacts([]);
  };

  const handleOpenChange = (open) => {
    setOpenNewContact(open);
    if (!open) {
      setSearchTerm("");
      setSearchedContacts([]);
    }
  };

  return (
    <>
      <TooltipProvider delayDuration={200}>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={() => setOpenNewContact(true)}
              aria-label="New direct message"
              className="h-6 w-6 rounded-md flex items-center justify-center text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.07] transition-colors"
            >
              <Plus className="h-4 w-4" />
            </button>
          </TooltipTrigger>
          <TooltipContent>New direct message</TooltipContent>
        </Tooltip>
      </TooltipProvider>

      <Dialog open={openNewContact} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>New direct message</DialogTitle>
            <DialogDescription>
              Find someone by name or email address to start a private thread.
            </DialogDescription>
          </DialogHeader>

          <div className="relative">
            <Search className="h-4 w-4 text-zinc-500 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              autoFocus
              value={searchTerm}
              onChange={(e) => searchContacts(e.target.value)}
              placeholder="Search by name or email..."
              className="w-full h-10 rounded-xl bg-[#161920] border border-white/[0.09] pl-10 pr-3.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15"
            />
          </div>

          <ScrollArea className="h-[260px] -mx-1 px-1">
            {isSearching ? (
              <div className="h-[240px] flex items-center justify-center text-xs text-zinc-500">
                Searching directory...
              </div>
            ) : searchedContacts.length > 0 ? (
              <div className="space-y-1">
                {searchedContacts.map((contact, index) => {
                  const name = getDisplayName(contact);
                  return (
                    <button
                      type="button"
                      key={contact.id || index}
                      onClick={() => selectNewContact(contact)}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left hover:bg-white/[0.05] transition-colors"
                    >
                      <UserAvatar user={contact} size="md" />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-zinc-100 truncate">
                          {name}
                        </p>
                        <p className="text-xs text-zinc-500 truncate">
                          {contact.email}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="h-[240px] flex flex-col items-center justify-center text-center px-6 gap-2.5">
                <div className="h-10 w-10 rounded-xl bg-white/[0.04] border border-white/[0.07] flex items-center justify-center text-zinc-500">
                  <Users className="h-4 w-4" />
                </div>
                <div className="space-y-1">
                  <p className="text-sm font-medium text-zinc-300">
                    {searchTerm.trim()
                      ? "No matching contacts"
                      : "Search for a contact"}
                  </p>
                  <p className="text-xs text-zinc-500">
                    {searchTerm.trim()
                      ? "Check the spelling or try a different email."
                      : "Start typing a name or email address above."}
                  </p>
                </div>
              </div>
            )}
          </ScrollArea>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default NewDm;
