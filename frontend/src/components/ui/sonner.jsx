import { Toaster as Sonner } from "sonner";

const Toaster = ({ ...props }) => {
  return (
    <Sonner
      theme="dark"
      className="toaster group"
      toastOptions={{
        classNames: {
          toast:
            "group toast bg-[#14161B] text-zinc-100 border border-white/[0.09] shadow-2xl rounded-xl text-[13px] font-medium",
          description: "text-zinc-400 text-xs",
          actionButton:
            "bg-zinc-100 text-zinc-950 font-medium hover:bg-white",
          cancelButton:
            "bg-zinc-800 text-zinc-300 hover:bg-zinc-700",
          closeButton:
            "bg-[#14161B] border border-white/[0.1] text-zinc-400 hover:text-zinc-100",
          error: "border-red-500/25 text-red-200",
          success: "border-emerald-500/25 text-emerald-100",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
