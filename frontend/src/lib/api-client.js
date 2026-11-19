import axios from "axios";
import { CSRF_ROUTE, HOST } from "../utils/constants.js";

const MUTATING_METHODS = new Set(["post", "put", "patch", "delete"]);

let cachedCsrfToken = null;
let csrfFetchPromise = null;
let unauthorizedHandler = null;

function readCookie(name) {
  if (typeof document === "undefined" || !document.cookie) {
    return null;
  }
  const cookies = document.cookie.split(";");
  for (const part of cookies) {
    const trimmed = part.trim();
    if (trimmed.startsWith(`${name}=`)) {
      return decodeURIComponent(trimmed.slice(name.length + 1));
    }
  }
  return null;
}

export function getCsrfToken() {
  return readCookie("XSRF-TOKEN") || cachedCsrfToken;
}

export function clearCsrfToken() {
  cachedCsrfToken = null;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export const apiClient = axios.create({
  withCredentials: true,
  baseURL: HOST || undefined,
  timeout: 30000,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

async function ensureCsrfToken() {
  const existing = getCsrfToken();
  if (existing) {
    return existing;
  }
  if (!csrfFetchPromise) {
    csrfFetchPromise = apiClient
      .get(CSRF_ROUTE)
      .then((res) => {
        const token =
          res?.headers?.["x-xsrf-token"] ||
          res?.data?.token ||
          readCookie("XSRF-TOKEN") ||
          null;
        if (token) {
          cachedCsrfToken = token;
        }
        return token;
      })
      .catch(() => null)
      .finally(() => {
        csrfFetchPromise = null;
      });
  }
  return csrfFetchPromise;
}

apiClient.interceptors.request.use(async (config) => {
  const method = (config.method || "get").toLowerCase();
  if (MUTATING_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      config.headers = config.headers || {};
      config.headers["X-XSRF-TOKEN"] = token;
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    const headerToken = response?.headers?.["x-xsrf-token"];
    if (headerToken) {
      cachedCsrfToken = headerToken;
    }
    return response;
  },
  async (error) => {
    const headerToken = error?.response?.headers?.["x-xsrf-token"];
    if (headerToken) {
      cachedCsrfToken = headerToken;
    }

    const originalRequest = error?.config;
    const status = error?.response?.status;

    // Retry once if CSRF token was stale or rotated
    if (
      status === 403 &&
      originalRequest &&
      !originalRequest._csrfRetried &&
      MUTATING_METHODS.has((originalRequest.method || "").toLowerCase())
    ) {
      originalRequest._csrfRetried = true;
      cachedCsrfToken = null;
      const freshToken = await ensureCsrfToken();
      if (freshToken) {
        originalRequest.headers = originalRequest.headers || {};
        originalRequest.headers["X-XSRF-TOKEN"] = freshToken;
        return apiClient(originalRequest);
      }
    }

    // Notify app when an authenticated session expires (skip auth endpoints)
    if (status === 401 && typeof unauthorizedHandler === "function") {
      const url = originalRequest?.url || "";
      if (!url.includes("/api/auth/") && !url.includes("/api/user/info")) {
        unauthorizedHandler(error);
      }
    }

    return Promise.reject(error);
  }
);

export function extractApiErrorMessage(error, fallback = "Something went wrong. Please try again.") {
  if (!error) return fallback;
  if (error.name === "CanceledError" || error.code === "ERR_CANCELED") {
    return null;
  }
  const data = error?.response?.data;
  if (typeof data?.message === "string" && data.message.trim()) {
    return data.message;
  }
  if (data?.errors && typeof data.errors === "object") {
    const firstErr = Object.values(data.errors).find((v) => typeof v === "string" && v.trim());
    if (firstErr) return firstErr;
  }
  if (error.code === "ECONNABORTED") {
    return "Request timed out. Check your connection and try again.";
  }
  if (!error.response) {
    return "Unable to reach the server. Please check your connection.";
  }
  return fallback;
}
