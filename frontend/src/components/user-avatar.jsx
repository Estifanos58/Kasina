import { Avatar, AvatarImage } from "@/components/ui/avatar";
import { cn, getColor, getInitials } from "@/lib/utils";

const sizeMap = {
  xs: { container: "h-7 w-7", text: "text-[11px]", dot: "h-2 w-2 right-0 bottom-0" },
  sm: { container: "h-9 w-9", text: "text-xs", dot: "h-2.5 w-2.5 -right-0.5 -bottom-0.5" },
  md: { container: "h-10 w-10", text: "text-sm", dot: "h-2.5 w-2.5 right-0 bottom-0" },
  lg: { container: "h-12 w-12", text: "text-base", dot: "h-3 w-3 right-0 bottom-0" },
  xl: { container: "h-24 w-24", text: "text-2xl", dot: "h-4 w-4 right-1 bottom-1" },
};

const UserAvatar = ({
  user,
  image,
  name,
  color,
  size = "md",
  status, // "online" | "offline" | undefined
  className,
}) => {
  const s = sizeMap[size] || sizeMap.md;
  const src = image !== undefined ? image : user?.image;
  const colorIndex = color !== undefined ? color : user?.color;
  const initials = getInitials(user, name);

  return (
    <div className={cn("relative shrink-0 inline-flex", s.container, className)}>
      <Avatar className={cn("w-full h-full rounded-full overflow-hidden")}>
        {src ? (
          <AvatarImage
            src={src}
            alt={name || "User avatar"}
            className="object-cover w-full h-full bg-zinc-900"
          />
        ) : (
          <div
            className={cn(
              "w-full h-full flex items-center justify-center rounded-full font-semibold tracking-tight select-none",
              s.text,
              getColor(colorIndex)
            )}
          >
            {initials}
          </div>
        )}
      </Avatar>
      {status && (
        <span
          className={cn(
            "absolute rounded-full ring-2 ring-[#111317]",
            s.dot,
            status === "online" ? "bg-emerald-500" : "bg-zinc-600"
          )}
        />
      )}
    </div>
  );
};

export default UserAvatar;
