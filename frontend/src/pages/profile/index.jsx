import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import imageCompression from "browser-image-compression";
import {
  ArrowLeft,
  Camera,
  Check,
  Loader2,
  Lock,
  Trash2,
  Upload,
} from "lucide-react";
import { useAppStore } from "@/store";
import { apiClient, extractApiErrorMessage } from "@/lib/api-client";
import {
  REMOVE_PROFILE_IMAGE_ROUTE,
  UPDATE_PROFILE_ROUTE,
} from "@/utils/constants";
import { getFirstName, getLastName, swatchColors } from "@/lib/utils";
import UserAvatar from "@/components/user-avatar";
import KasinaLogo from "@/components/kasina-logo";

const Profile = () => {
  const { userInfo, setUserInfo } = useAppStore();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [image, setImage] = useState(null);
  const [selectedColor, setSelectedColor] = useState(0);
  const [isLoading, setLoading] = useState(false);
  const [isCompressing, setIsCompressing] = useState(false);
  const [isRemovingImage, setIsRemovingImage] = useState(false);
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  useEffect(() => {
    if (!userInfo) return;
    if (userInfo.profileSetup) {
      setFirstName(getFirstName(userInfo));
      setLastName(getLastName(userInfo));
      setSelectedColor(
        typeof userInfo.color === "number" ? userInfo.color : 0
      );
    }
    if (userInfo.image) {
      setImage(userInfo.image);
    }
  }, [userInfo]);

  const validateProfile = () => {
    if (!firstName.trim()) {
      toast.error("First name is required.");
      return false;
    }
    if (!lastName.trim()) {
      toast.error("Last name is required.");
      return false;
    }
    return true;
  };

  const saveChanges = async (e) => {
    e.preventDefault();
    if (!validateProfile() || isLoading) return;

    try {
      setLoading(true);
      const response = await apiClient.post(
        UPDATE_PROFILE_ROUTE,
        {
          firstname: firstName.trim(),
          lastname: lastName.trim(),
          color: selectedColor,
          image: image || "",
        },
        { withCredentials: true }
      );
      if (response.status === 200 && response.data) {
        setUserInfo({ ...response.data });
        toast.success("Profile saved.");
        navigate("/chat");
      }
    } catch (err) {
      const msg = extractApiErrorMessage(
        err,
        "Failed to update profile. Try again."
      );
      if (msg) toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleNavigateBack = () => {
    if (userInfo?.profileSetup) {
      navigate("/chat");
    } else {
      toast.error("Please complete your profile before continuing.");
    }
  };

  const handleFileInputClick = () => {
    fileInputRef.current?.click();
  };

  const handleImageChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      setIsCompressing(true);
      const options = {
        maxSizeMB: 1,
        maxWidthOrHeight: 1920,
        useWebWorker: true,
      };
      const compressedFile = await imageCompression(file, options);
      const reader = new FileReader();
      reader.onloadend = () => {
        setImage(reader.result);
        setIsCompressing(false);
      };
      reader.onerror = () => {
        setIsCompressing(false);
        toast.error("Could not read image file.");
      };
      reader.readAsDataURL(compressedFile);
    } catch (_error) {
      setIsCompressing(false);
      toast.error("Failed to process image.");
    } finally {
      if (event.target) event.target.value = "";
    }
  };

  const handleDeleteImage = async () => {
    if (!image || isRemovingImage) return;
    // If image wasn't persisted on server yet, clear locally
    if (!userInfo?.image) {
      setImage(null);
      return;
    }
    try {
      setIsRemovingImage(true);
      const response = await apiClient.delete(REMOVE_PROFILE_IMAGE_ROUTE, {
        withCredentials: true,
      });
      if (response.status === 200) {
        setUserInfo({ ...userInfo, image: null });
        setImage(null);
        toast.success("Profile photo removed.");
      }
    } catch (_error) {
      toast.error("Failed to remove profile photo.");
    } finally {
      setIsRemovingImage(false);
    }
  };

  const previewUser = {
    firstname: firstName,
    lastname: lastName,
    email: userInfo?.email,
    image,
    color: selectedColor,
  };

  return (
    <div className="min-h-dvh w-full bg-[#0B0C0E] text-zinc-100 flex flex-col">
      {/* Top navigation bar */}
      <header className="h-16 px-6 border-b border-white/[0.06] flex items-center justify-between shrink-0">
        {userInfo?.profileSetup ? (
          <button
            type="button"
            onClick={handleNavigateBack}
            className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-zinc-100 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Back to messages</span>
          </button>
        ) : (
          <KasinaLogo size="sm" />
        )}
        <span className="text-xs font-mono text-zinc-500">
          {userInfo?.profileSetup ? "Account settings" : "Onboarding"}
        </span>
      </header>

      {/* Main content */}
      <main className="flex-1 flex items-center justify-center px-4 py-10 sm:px-6">
        <div className="w-full max-w-[520px] space-y-6">
          <div className="space-y-1.5">
            <h1 className="text-2xl font-semibold tracking-[-0.025em] text-zinc-50">
              {userInfo?.profileSetup ? "Profile settings" : "Set up your profile"}
            </h1>
            <p className="text-sm text-zinc-400">
              {userInfo?.profileSetup
                ? "Update how your name and avatar appear across conversations."
                : "Add your name and optionally choose a photo or avatar accent."}
            </p>
          </div>

          <form
            onSubmit={saveChanges}
            className="rounded-2xl border border-white/[0.08] bg-[#111317] p-6 sm:p-7 space-y-6 shadow-xl"
          >
            {/* Avatar section */}
            <div className="flex flex-col sm:flex-row sm:items-center gap-5 pb-6 border-b border-white/[0.06]">
              <button
                type="button"
                onClick={handleFileInputClick}
                className="group relative h-24 w-24 rounded-full shrink-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500"
                aria-label="Upload profile photo"
              >
                <UserAvatar user={previewUser} size="xl" />
                <div className="absolute inset-0 rounded-full bg-black/55 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  {isCompressing ? (
                    <Loader2 className="h-5 w-5 text-white animate-spin" />
                  ) : (
                    <Camera className="h-5 w-5 text-white" />
                  )}
                </div>
              </button>

              <input
                type="file"
                ref={fileInputRef}
                className="hidden"
                onChange={handleImageChange}
                name="profile-image"
                accept=".png, .jpg, .jpeg, .webp"
              />

              <div className="space-y-2.5">
                <div>
                  <p className="text-sm font-medium text-zinc-100">
                    Profile photo
                  </p>
                  <p className="text-xs text-zinc-500 mt-0.5">
                    PNG, JPG, or WEBP. Automatically compressed.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={handleFileInputClick}
                    disabled={isCompressing}
                    className="inline-flex items-center gap-1.5 h-8 px-3 rounded-lg bg-[#1A1D24] hover:bg-[#222630] border border-white/[0.08] text-xs font-medium text-zinc-200 transition-colors disabled:opacity-50"
                  >
                    <Upload className="h-3.5 w-3.5" />
                    <span>{image ? "Change photo" : "Upload photo"}</span>
                  </button>
                  {image && (
                    <button
                      type="button"
                      onClick={handleDeleteImage}
                      disabled={isRemovingImage}
                      className="inline-flex items-center gap-1.5 h-8 px-3 rounded-lg bg-red-500/10 hover:bg-red-500/15 border border-red-500/20 text-xs font-medium text-red-300 transition-colors disabled:opacity-50"
                    >
                      {isRemovingImage ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Trash2 className="h-3.5 w-3.5" />
                      )}
                      <span>Remove</span>
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Form inputs */}
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label
                  htmlFor="profile-email"
                  className="block text-xs font-medium text-zinc-400"
                >
                  Email address
                </label>
                <div className="relative">
                  <input
                    id="profile-email"
                    type="email"
                    disabled
                    value={userInfo?.email || ""}
                    className="w-full h-11 rounded-xl bg-[#0D0F13] border border-white/[0.06] pl-3.5 pr-10 text-sm text-zinc-500 cursor-not-allowed"
                  />
                  <Lock className="h-3.5 w-3.5 text-zinc-600 absolute right-3.5 top-1/2 -translate-y-1/2" />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label
                    htmlFor="profile-first-name"
                    className="block text-xs font-medium text-zinc-300"
                  >
                    First name
                  </label>
                  <input
                    id="profile-first-name"
                    type="text"
                    placeholder="Alex"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    disabled={isLoading}
                    className="w-full h-11 rounded-xl bg-[#161920] border border-white/[0.09] px-3.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15"
                  />
                </div>

                <div className="space-y-1.5">
                  <label
                    htmlFor="profile-last-name"
                    className="block text-xs font-medium text-zinc-300"
                  >
                    Last name
                  </label>
                  <input
                    id="profile-last-name"
                    type="text"
                    placeholder="Rivera"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    disabled={isLoading}
                    className="w-full h-11 rounded-xl bg-[#161920] border border-white/[0.09] px-3.5 text-sm text-zinc-100 placeholder:text-zinc-500 outline-none transition-all focus:border-emerald-500/60 focus:ring-2 focus:ring-emerald-500/15"
                  />
                </div>
              </div>

              {/* Avatar color accent picker */}
              <div className="space-y-2 pt-1">
                <span className="block text-xs font-medium text-zinc-300">
                  Avatar accent
                </span>
                <div className="flex items-center gap-2.5">
                  {swatchColors.map((swatch, index) => {
                    const isSelected = selectedColor === index;
                    return (
                      <button
                        type="button"
                        key={swatch.label}
                        title={swatch.label}
                        aria-label={`Select ${swatch.label} avatar color`}
                        onClick={() => setSelectedColor(index)}
                        className={`h-8 w-8 rounded-full flex items-center justify-center transition-all ${
                          swatch.dot
                        } ${
                          isSelected
                            ? `ring-2 ring-offset-2 ring-offset-[#111317] ${swatch.ring} scale-105`
                            : "opacity-70 hover:opacity-100"
                        }`}
                      >
                        {isSelected && (
                          <Check className="h-3.5 w-3.5 text-zinc-950 stroke-[2.75]" />
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Submit */}
            <div className="pt-2 flex items-center justify-end gap-3">
              {userInfo?.profileSetup && (
                <button
                  type="button"
                  onClick={handleNavigateBack}
                  className="h-10 px-4 rounded-xl text-sm font-medium text-zinc-400 hover:text-zinc-100 hover:bg-white/[0.05] transition-colors"
                >
                  Cancel
                </button>
              )}
              <button
                type="submit"
                disabled={isLoading}
                className="h-10 px-5 rounded-xl bg-zinc-100 hover:bg-white active:scale-[0.99] text-zinc-950 text-sm font-medium inline-flex items-center justify-center gap-2 transition-all disabled:opacity-60 disabled:pointer-events-none"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : (
                  <span>
                    {userInfo?.profileSetup
                      ? "Save changes"
                      : "Continue to workspace"}
                  </span>
                )}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};

export default Profile;
