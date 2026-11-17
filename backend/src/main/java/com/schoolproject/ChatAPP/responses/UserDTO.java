package com.schoolproject.ChatAPP.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.schoolproject.ChatAPP.model.User;

public class UserDTO {
    private String id;
    private String email;
    private String firstname;
    private String lastname;
    private int color;
    private String image;
    private boolean profileSetup;

    public UserDTO() {}

    public UserDTO(String id, String email, String firstname, String lastname, int color, String image, boolean profileSetup) {
        this.id = id;
        this.email = email;
        this.firstname = firstname;
        this.lastname = lastname;
        this.color = color;
        this.image = image;
        this.profileSetup = profileSetup;
    }

    public static UserDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstname(),
                user.getLastname(),
                user.getColor(),
                user.getImage(),
                user.isProfileSetup()
        );
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

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    // Alias for frontend components expecting firstName
    @JsonProperty("firstName")
    public String getFirstName() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    // Alias for frontend components expecting lastName
    @JsonProperty("lastName")
    public String getLastName() {
        return lastname;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public boolean isProfileSetup() {
        return profileSetup;
    }

    public void setProfileSetup(boolean profileSetup) {
        this.profileSetup = profileSetup;
    }
}
