package com.schoolproject.ChatAPP.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class ContactResponseDTO {
    private String id;
    private String email;
    private String firstname;
    private String lastname;
    private String image;
    private int color;
    private Instant lastMessageTime;

    public ContactResponseDTO() {}

    public ContactResponseDTO(String id, String email, String firstname, String lastname, String image, int color, Instant lastMessageTime) {
        this.id = id;
        this.email = email;
        this.firstname = firstname;
        this.lastname = lastname;
        this.image = image;
        this.color = color;
        this.lastMessageTime = lastMessageTime;
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

    @JsonProperty("lastName")
    public String getLastName() {
        return lastname;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Instant lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
}
