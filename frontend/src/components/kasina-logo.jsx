import { cn } from "@/lib/utils";

const KasinaLogo = ({ size = "md", showWordmark = true, className }) => {
  const sizes = {
    sm: { box: "h-7 w-7 rounded-lg", svg: "h-4 w-4", text: "text-[15px]" },
    md: { box: "h-8 w-8 rounded-lg", svg: "h-[18px] w-[18px]", text: "text-base" },
    lg: { box: "h-10 w-10 rounded-xl", svg: "h-5 w-5", text: "text-lg" },
  };
  const s = sizes[size] || sizes.md;

  return (
    <div className={cn("inline-flex items-center gap-2.5 select-none", className)}>
      <div
        className={cn(
          "flex items-center justify-center bg-[#15181E] border border-white/[0.1] shadow-sm",
          s.box
        )}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          className={s.svg}
          aria-hidden="true"
        >
          <path
            d="M6.5 4.75V19.25M6.5 12L15.25 4.75M10.5 9.25L18 19.25"
            stroke="#10B981"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
      {showWordmark && (
        <span
          className={cn(
            "font-semibold tracking-[-0.025em] text-zinc-100",
            s.text
          )}
        >
          Kasina
        </span>
      )}
    </div>
  );
};

export default KasinaLogo;
