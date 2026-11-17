package com.schoolproject.ChatAPP.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignUpResponse {
    public String id;
    public String email;
    public boolean profileSetup;

    public SignUpResponse() {}

    public SignUpResponse(String id, String email, boolean profileSetup) {
        this.id = id;
        this.email = email;
        this.profileSetup = profileSetup;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isProfileSetup() {
        return profileSetup;
    }

    public void setProfileSetup(boolean profileSetup) {
        this.profileSetup = profileSetup;
    }

    // Alias for backward compatibility
    @JsonProperty("profileStatus")
    public boolean getProfileStatus() {
        return profileSetup;
    }
}
