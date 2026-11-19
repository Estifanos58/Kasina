package com.schoolproject.ChatAPP.repository;

import java.time.Instant;

public class DmContactSummary {
    private String contactId;
    private Instant lastMessageTime;

    public DmContactSummary() {}

    public DmContactSummary(String contactId, Instant lastMessageTime) {
        this.contactId = contactId;
        this.lastMessageTime = lastMessageTime;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Instant lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
}
