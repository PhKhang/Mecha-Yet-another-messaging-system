package com.example.mechaadmin.dao;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "chats")
public class ChatDAO {
    @Id
    @Column(name = "chat_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer chatId;
    @Column(name = "group_name")
    private String groupName;
    @Column(name = "chat_type")
    private String chatType;
    @Column(name = "admin_id")
    private Integer adminId;
    @Column(name = "created_at")
    private LocalDate createdAt;

    public ChatDAO() {
        this.chatId = 0;
        this.groupName = "";
        this.chatType = "";
        this.adminId = 0;
        this.createdAt = LocalDate.now();
    }

    public ChatDAO(int chatId, String groupName, String chatType, int admindId, LocalDate createdAt) {
        this.chatId = chatId;
        this.groupName = groupName;
        this.chatType = chatType;
        this.adminId = admindId;
        this.createdAt = createdAt;
    }

    public Integer getChatId() {
        return chatId;
    }

    public void setChatId(int chatId) {
        this.chatId = chatId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public Integer getAdminId() {
        return adminId;
    }

    public void setAdminId(int admindId) {
        this.adminId = admindId;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }
}