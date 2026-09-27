package com.chaihq.webapp.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Calendar;

// An image or file added through the rich text editor. Referenced from the saved HTML
// by its random token, e.g. /attachments/{token}/{fileName}.
@Entity(name = "editor_attachments")
public class EditorAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "token", unique = true, nullable = false, length = 64)
    private String token;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "byte_size")
    private long byteSize;

    // Base64 MD5 sent by the browser before upload; the uploaded bytes must match it
    @Column(name = "checksum")
    private String checksum;

    // Null until the browser has uploaded the file
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY)
    @Column(name = "data")
    private byte[] data;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private Calendar createdAt;

    public long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }
}
