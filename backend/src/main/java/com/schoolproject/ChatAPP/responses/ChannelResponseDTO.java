package com.schoolproject.ChatAPP.responses;

import com.schoolproject.ChatAPP.model.Channel;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelResponseDTO {
    private String id;
    private String name;
    private UserDTO admin;
    private List<UserDTO> members;
    private Date createdAt;
    private Date updatedAt;

    public ChannelResponseDTO() {}

    public ChannelResponseDTO(String id, String name, UserDTO admin, List<UserDTO> members, Date createdAt, Date updatedAt) {
        this.id = id;
        this.name = name;
        this.admin = admin;
        this.members = members;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ChannelResponseDTO fromEntity(Channel channel) {
        if (channel == null) {
            return null;
        }

        UserDTO adminDTO = channel.getAdmin() != null ? UserDTO.fromEntity(channel.getAdmin()) : null;
        List<UserDTO> memberDTOs = channel.getMembers() != null
                ? channel.getMembers().stream().map(UserDTO::fromEntity).collect(Collectors.toList())
                : Collections.emptyList();

        return new ChannelResponseDTO(
                channel.getId(),
                channel.getName(),
                adminDTO,
                memberDTOs,
                channel.getCreatedAt(),
                channel.getUpdatedAt()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserDTO getAdmin() {
        return admin;
    }

    public void setAdmin(UserDTO admin) {
        this.admin = admin;
    }

    public List<UserDTO> getMembers() {
        return members;
    }

    public void setMembers(List<UserDTO> members) {
        this.members = members;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
