import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import {
  ArrowRight,
  Eye,
  EyeOff,
  Hash,
  Loader2,
  Paperclip,
  Check,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { LOGIN_ROUTE, SIGNUP_ROUTES } from "@/utils/constants";
import { useAppStore } from "@/store";
import KasinaLogo from "@/components/kasina-logo";

const Auth = () => {
  const navigate = useNavigate();
  const { setUserInfo } = useAppStore();
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setLoading] = useState(false);

  const switchMode = (nextMode) => {
    setMode(nextMode);
    setConfirmPassword("");
    setShowPassword(false);
    setShowConfirmPassword(false);
  };

  const validateLogin = () => {
    if (!email.trim()) {
      toast.error("Please enter your email address.");
      return false;
    }
    if (!password) {
      toast.error("Please enter your password.");
      return false;
    }
    return true;
  };

  const validateSignUp = () => {
    if (!email.trim()) {
      toast.error("Please enter your email address.");
      return false;
    }
    if (!password) {
      toast.error("Please create a password.");
      return false;
    }
    if (password.length < 6) {
      toast.error("Password must be at least 6 characters.");
      return false;
    }
    if (password !== confirmPassword) {
      toast.error("Passwords do not match.");
      return false;
    }
    return true;
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    if (!validateLogin() || isLoading) return;

    try {
      setLoading(true);
      const response = await apiClient.post(LOGIN_ROUTE, {
        email: email.trim(),
        password,
      });
      if (response.data?.id) {
        setUserInfo(response.data);
        if (response.data.profileSetup) {
          toast.success("Welcome back.");
          navigate("/chat");
        } else {
          navigate("/profile");
        }
      } else {
        toast.error("Invalid credentials.");
      }
    } catch (error) {
      const msg =
        error?.response?.data?.message ||
        "Invalid credentials or server unavailable.";
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e) => {
    e.preventDefault();
    if (!validateSignUp() || isLoading) return;

    try {
      setLoading(true);
      const response = await apiClient.post(SIGNUP_ROUTES, {
        email: email.trim(),
        password,
      });
      if (response.data?.id) {
        setUserInfo(response.data);
        toast.success("Account created. Complete your profile to continue.");
        navigate("/profile");
      } else {
        toast.error("Unable to create account.");
      }
    } catch (error) {
      const msg =
        error?.response?.data?.message ||
        "Could not create account. Please try again.";
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const passwordsMatch =
    confirmPassword.length > 0 && password === confirmPassword;

  return (
    <div className="min-h-dvh w-full bg-[#0B0C0E] text-zinc-100 grid grid-cols-1 lg:grid-cols-[1.08fr_1fr]">
      {/* Left architectural product showcase (desktop) */}
      <aside className="hidden lg:flex flex-col justify-between relative overflow-hidden bg-[#0E1014] border-r border-white/[0.06] p-10 xl:p-14">
        {/* Subtle dot grid texture */}
        <div
          className="pointer-events-none absolute inset-0 bg-dot-grid opacity-60"
          style={{
            maskImage:
              "radial-gradient(ellipse 75% 70% at 35% 40%, black 30%, transparent 100%)",
            WebkitMaskImage:
              "radial-gradient(ellipse 75% 70% at 35% 40%, black 30%, transparent 100%)",
          }}
        />

        <div className="relative z-10">
          <KasinaLogo size="md" />
        </div>

        <div className="relative z-10 my-auto py-10 max-w-lg space-y-8">
          <div className="space-y-3.5">
            <h1 className="text-[34px] xl:text-[40px] font-semibold tracking-[-0.035em] leading-[1.12] text-zinc-50">
              Direct, fast, uncluttered messaging.
            </h1>
            <p className="text-[15px] text-zinc-400 leading-relaxed max-w-md">
              Private threads, organized team channels, and instant file sharing
              built for people who prefer focus over noise.
            </p>
          </div>

          {/* Crisp, restrained live interface preview */}
          <div className="rounded-2xl border border-white/[0.08] bg-[#12141A] shadow-2xl overflow-hidden">
            {/* Mock header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-white/[0.06] bg-[#14171D]">
              <div className="flex items-center gap-2.5">
                <div className="h-7 w-7 rounded-lg bg-white/[0.05] border border-white/[0.07] flex items-center justify-center text-zinc-300">
                  <Hash className="h-3.5 w-3.5" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-zinc-100 leading-none">
                    release-v2
                  </p>
                  <p className="text-[11px] text-zinc-500 mt-1 leading-none">
                    4 members
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1.5 text-[11px] font-mono text-emerald-400/90">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                live
              </div>
            </div>

            {/* Mock messages */}
            <div className="p-4 space-y-3.5 text-[13px]">
              <div className="flex items-start gap-2.5">
                <div className="h-7 w-7 rounded-full bg-sky-500/15 border border-sky-500/30 text-sky-300 flex items-center justify-center text-[11px] font-semibold shrink-0 mt-0.5">
                  MK
                </div>
                <div className="space-y-1 min-w-0">
                  <div className="flex items-baseline gap-2">
                    <span className="text-xs font-medium text-zinc-200">
                      Maya Kebede
                    </span>
                    <span className="text-[10px] font-mono text-zinc-500">
                      10:42
                    </span>
                  </div>
                  <div className="inline-block rounded-xl rounded-tl-sm bg-[#1A1D24] border border-white/[0.06] px-3 py-2 text-zinc-200 leading-snug">
                    Docker health checks are green across all three services.
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <div className="h-7 w-7 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-300 flex items-center justify-center text-[11px] font-semibold shrink-0 mt-0.5">
                  DT
                </div>
                <div className="space-y-1 min-w-0">
                  <div className="flex items-baseline gap-2">
                    <span className="text-xs font-medium text-zinc-200">
                      Daniel Tadesse
                    </span>
                    <span className="text-[10px] font-mono text-zinc-500">
                      10:43
                    </span>
                  </div>
                  <div className="inline-flex items-center gap-2 rounded-xl rounded-tl-sm bg-[#1A1D24] border border-white/[0.06] px-3 py-2 text-zinc-300">
                    <Paperclip className="h-3.5 w-3.5 text-zinc-400 shrink-0" />
                    <span className="font-mono text-xs truncate">
                      release-checklist.pdf
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex justify-end">
                <div className="space-y-1 text-right">
                  <div className="inline-block rounded-xl rounded-tr-sm bg-[#0F5146] border border-emerald-400/20 px-3 py-2 text-zinc-50 leading-snug">
                    Looks good — shipping to production now.
                  </div>
                  <div className="text-[10px] font-mono text-zinc-500">
                    10:44
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="relative z-10 flex items-center gap-4 text-xs text-zinc-500">
          <span>Real-time WebSocket sync</span>
          <span className="text-zinc-700">·</span>
          <span>Direct messages &amp; channels</span>
          <span className="text-zinc-700">·</span>
          <span>Instant file sharing</span>
        </div>
      </aside>

      {/* Right auth form panel */}
      <main className="flex flex-col justify-center items-center px-6 py-12 sm:px-10">
        <div className="w-full max-w-[380px] space-y-7">
          {/* Mobile brand header */}
          <div className="lg:hidden">
            <KasinaLogo size="md" />
          </div>

          <div className="space-y-1.5">
            <h2 className="text-2xl font-semibold tracking-[-0.025em] text-zinc-50">
              {mode === "login" ? "Sign in to Kasina" : "Create your account"}
            </h2>
            <p className="text-sm text-zinc-400">
              {mode === "login"
                ? "Enter your email and password to open your workspace."
                : "Get started with your email address in a few seconds."}
            </p>
          </div>

          {/* Segmented mode switcher */}
          <div
            role="tablist"
            aria-label="Authentication mode"
            className="grid grid-cols-2 p-1 rounded-xl bg-[#13151B] border border-white/[0.07]"
          >
            <button
              type="button"
              role="tab"
              aria-selected={mode === "login"}
              onClick={() => switchMode("login")}
              className={`h-9 rounded-lg text-sm font-medium transition-all ${
                mode === "login"
                  ? "bg-[#1E222B] text-zinc-100 shadow-sm border border-white/[0.07]"
                  : "text-zinc-400 hover:text-zinc-200"
              }`}
            >
              Sign in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === "signup"}
              onClick={() => switchMode("signup")}
              className={`h-9 rounded-lg text-sm font-medium transition-all ${
                mode === "signup"
                  ? "bg-[#1E222B] text-zinc-100 shadow-sm border border-white/[0.07]"
                  : "text-zinc-400 hover:text-zinc-200"
              }`}
            >
              Create account
            </button>
          </div>

          <form
            onSubmit={mode === "login" ? handleLogin : handleSignup}
            className="space-y-4"
            noValidate
          >
            <div className="space-y-1.5">
              <label
                htmlFor="auth-email"
                className="block text-xs font-medium text-zinc-300"
              >
                Email address
              </label>
              <input
                id="auth-email"
                type="email"
                autoComplete="email"
                autoFocus
                placeholder="name@company.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={isLoading}
                className="w-full h-11 rounded-xl bg-[#13151B] border border-white/[0.09] px-3.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15 disabled:opacity-50"
              />
            </div>

            <div className="space-y-1.5">
              <label
                htmlFor="auth-password"
                className="block text-xs font-medium text-zinc-300"
              >
                Password
              </label>
              <div className="relative">
                <input
                  id="auth-password"
                  type={showPassword ? "text" : "password"}
                  autoComplete={
                    mode === "login" ? "current-password" : "new-password"
                  }
                  placeholder={
                    mode === "login" ? "Enter your password" : "Min. 6 characters"
                  }
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isLoading}
                  className="w-full h-11 rounded-xl bg-[#13151B] border border-white/[0.09] pl-3.5 pr-10 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15 disabled:opacity-50"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-zinc-500 hover:text-zinc-200 transition-colors"
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            {mode === "signup" && (
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label
                    htmlFor="auth-confirm-password"
                    className="block text-xs font-medium text-zinc-300"
                  >
                    Confirm password
                  </label>
                  {passwordsMatch && (
                    <span className="inline-flex items-center gap-1 text-[11px] text-emerald-400">
                      <Check className="h-3 w-3" />
                      Matches
                    </span>
                  )}
                </div>
                <div className="relative">
                  <input
                    id="auth-confirm-password"
                    type={showConfirmPassword ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Re-enter your password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={isLoading}
                    className="w-full h-11 rounded-xl bg-[#13151B] border border-white/[0.09] pl-3.5 pr-10 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15 disabled:opacity-50"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((prev) => !prev)}
                    aria-label={
                      showConfirmPassword
                        ? "Hide confirm password"
                        : "Show confirm password"
                    }
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-zinc-500 hover:text-zinc-200 transition-colors"
                  >
                    {showConfirmPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={isLoading}
              className="w-full h-11 mt-2 rounded-xl bg-zinc-100 hover:bg-white active:scale-[0.99] text-zinc-950 font-medium text-sm flex items-center justify-center gap-2 transition-all disabled:opacity-60 disabled:pointer-events-none"
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>
                    {mode === "login" ? "Signing in..." : "Creating account..."}
                  </span>
                </>
              ) : (
                <>
                  <span>
                    {mode === "login" ? "Sign in" : "Create account"}
                  </span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <p className="text-center text-xs text-zinc-500">
            {mode === "login" ? (
              <>
                New to Kasina?{" "}
                <button
                  type="button"
                  onClick={() => switchMode("signup")}
                  className="text-zinc-200 hover:text-white font-medium underline-offset-4 hover:underline"
                >
                  Create an account
                </button>
              </>
            ) : (
              <>
                Already have an account?{" "}
                <button
                  type="button"
                  onClick={() => switchMode("login")}
                  className="text-zinc-200 hover:text-white font-medium underline-offset-4 hover:underline"
                >
                  Sign in
                </button>
              </>
            )}
          </p>
        </div>
      </main>
    </div>
  );
};

export default Auth;
