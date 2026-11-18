import ChatHeader from "./components/chat-header";
import MessageBar from "./components/message-bar";
import MessageContainer from "./components/message-container";

const ChatContainer = () => {
  return (
    <section className="flex-1 flex flex-col min-w-0 h-full bg-[#0B0C0E]">
      <ChatHeader />
      <MessageContainer />
      <MessageBar />
    </section>
  );
};

export default ChatContainer;
