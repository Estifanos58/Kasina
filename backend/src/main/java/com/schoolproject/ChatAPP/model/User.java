package com.schoolproject.ChatAPP.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

    // No-argument constructor (required by Jackson)
    public User() {}

    @JsonCreator
    public User(@JsonProperty("id") String id) {
        this.id = id;
    }
    @Id
    private String id;

    @NotBlank(message = "Email is required")
    @Indexed(unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Indexed
    private String firstname;

    @Indexed
    private String lastname;
    private int color;
    private String image;
    private boolean profileSetup = false;


    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public int getColor() {
        return color;
    }

    public String getImage() {
        return image;
    }

    public boolean isProfileSetup() {
        return profileSetup;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setProfileSetup(boolean profileSetup) {
        this.profileSetup = profileSetup;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", firstname='" + firstname + '\'' +
                ", lastname='" + lastname + '\'' +
                ", color=" + color +
                ", profileSetup=" + profileSetup +
                '}';
    }

    public String getId() {
        return id;
    }
}
