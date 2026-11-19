import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import moment from "moment";

export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

export const colors = [
  "bg-emerald-500/15 text-emerald-300 border border-emerald-500/30",
  "bg-sky-500/15 text-sky-300 border border-sky-500/30",
  "bg-amber-500/15 text-amber-300 border border-amber-500/30",
  "bg-rose-500/15 text-rose-300 border border-rose-500/30",
  "bg-indigo-500/15 text-indigo-300 border border-indigo-500/30",
  "bg-zinc-500/20 text-zinc-200 border border-zinc-500/30",
];

export const swatchColors = [
  { label: "Emerald", dot: "bg-emerald-500", ring: "ring-emerald-400" },
  { label: "Sky", dot: "bg-sky-500", ring: "ring-sky-400" },
  { label: "Amber", dot: "bg-amber-500", ring: "ring-amber-400" },
  { label: "Rose", dot: "bg-rose-500", ring: "ring-rose-400" },
  { label: "Indigo", dot: "bg-indigo-500", ring: "ring-indigo-400" },
  { label: "Zinc", dot: "bg-zinc-400", ring: "ring-zinc-300" },
];

export const getColor = (color) => {
  if (typeof color === "number" && color >= 0 && color < colors.length) {
    return colors[color];
  }
  return colors[0];
};

export const getFirstName = (user) => user?.firstname || user?.firstName || "";

export const getLastName = (user) => user?.lastname || user?.lastName || "";

export const getDisplayName = (entity, isChannel = false) => {
  if (!entity) return "";
  if (isChannel) return entity.name || "Untitled channel";
  const first = getFirstName(entity);
  const last = getLastName(entity);
  if (first || last) return `${first} ${last}`.trim();
  return entity.email || "Unknown user";
};

export const getInitials = (entity, fallbackName = "") => {
  if (!entity && !fallbackName) return "?";
  const first = getFirstName(entity);
  const last = getLastName(entity);
  if (first && last) return `${first[0]}${last[0]}`.toUpperCase();
  if (first) return first.slice(0, 2).toUpperCase();
  if (fallbackName) {
    const parts = fallbackName.trim().split(/\s+/);
    if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    return fallbackName.slice(0, 2).toUpperCase();
  }
  if (entity?.email) return entity.email[0].toUpperCase();
  return "?";
};

export const formatSidebarTime = (timestamp) => {
  if (!timestamp) return "";
  const m = moment(timestamp);
  if (!m.isValid()) return "";
  const now = moment();
  if (m.isSame(now, "day")) return m.format("HH:mm");
  if (m.isSame(now.clone().subtract(1, "day"), "day")) return "Yesterday";
  if (m.isSame(now, "week")) return m.format("ddd");
  if (m.isSame(now, "year")) return m.format("MMM D");
  return m.format("MM/DD/YY");
};

export const formatDateDivider = (timestamp) => {
  if (!timestamp) return "";
  const m = moment(timestamp);
  if (!m.isValid()) return "";
  const now = moment();
  if (m.isSame(now, "day")) return "Today";
  if (m.isSame(now.clone().subtract(1, "day"), "day")) return "Yesterday";
  if (m.isSame(now, "year")) return m.format("MMMM D");
  return m.format("MMMM D, YYYY");
};
