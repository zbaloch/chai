package com.chaihq.webapp.controllers;

import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.services.ProjectAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Uploads for the Lexxy rich text editor. Lexxy speaks the Active Storage direct upload
 * protocol: it POSTs the file's metadata, then PUTs the bytes to the URL we hand back,
 * and finally builds the file's URL from its data-blob-url-template.
 */
@RestController
public class AttachmentsController {

    // Matches spring.servlet.multipart.max-file-size and nginx client_max_body_size
    private static final long MAX_BYTES = 100L * 1024 * 1024;

    // Images a browser can safely show inline. Anything else (SVG, HTML, PDF...) is
    // served as a download so user uploads can't run script on this origin.
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif");

    private final EditorAttachmentRepository attachmentRepository;
    private final ProjectRepository projectRepository;
    private final MessageRepository messageRepository;
    private final CommentRepository commentRepository;
    private final TodoRepository todoRepository;
    private final ProjectAccess projectAccess;

    public AttachmentsController(EditorAttachmentRepository attachmentRepository, ProjectRepository projectRepository,
                                 MessageRepository messageRepository, CommentRepository commentRepository,
                                 TodoRepository todoRepository, ProjectAccess projectAccess) {
        this.attachmentRepository = attachmentRepository;
        this.projectRepository = projectRepository;
        this.messageRepository = messageRepository;
        this.commentRepository = commentRepository;
        this.todoRepository = todoRepository;
        this.projectAccess = projectAccess;
    }

    @PostMapping(path = Paths.ACCOUNT + "/project/{projectId}/attachments/direct_uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDirectUpload(@PathVariable Long projectId, @RequestBody Map<String, Map<String, Object>> body,
                                                HttpServletRequest request) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(projectId, currentUser);
        Map<String, Object> blob = body.get("blob");
        if (blob == null) {
            return ResponseEntity.badRequest().build();
        }

        long byteSize = ((Number) blob.getOrDefault("byte_size", 0)).longValue();
        if (byteSize <= 0 || byteSize > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        String contentType = String.valueOf(blob.getOrDefault("content_type", ""));
        if (contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        EditorAttachment attachment = new EditorAttachment();
        attachment.setToken(UUID.randomUUID().toString().replace("-", ""));
        attachment.setFileName(safeFileName(String.valueOf(blob.getOrDefault("filename", "file"))));
        attachment.setContentType(contentType);
        attachment.setByteSize(byteSize);
        attachment.setChecksum((String) blob.get("checksum"));
        attachment.setUserId(currentUser.getId());
        attachment.setProjectId(project.getId());
        attachment.setCreatedAt(Calendar.getInstance());
        attachmentRepository.save(attachment);

        String uploadUrl = request.getContextPath() + "/attachments/" + attachment.getToken() + "/upload";
        return ResponseEntity.ok(Map.of(
                "signed_id", attachment.getToken(),
                "attachable_sgid", attachment.getToken(),
                "filename", attachment.getFileName(),
                "content_type", contentType,
                "byte_size", byteSize,
                "previewable", false,
                "direct_upload", Map.of(
                        "url", uploadUrl,
                        "headers", uploadHeaders(contentType, request))));
    }

    @PutMapping("/attachments/{token}/upload")
    public ResponseEntity<?> upload(@PathVariable String token, HttpServletRequest request) throws IOException {
        User currentUser = projectAccess.currentUser();
        EditorAttachment attachment = attachmentRepository.findByToken(token).orElse(null);
        if (attachment == null || attachment.getUserId() == null || attachment.getUserId() != currentUser.getId()) {
            throw projectAccess.denied(currentUser, "attachment upload", token);
        }
        if (attachment.getData() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        byte[] data = readAtMost(request.getInputStream(), attachment.getByteSize());
        if (data == null || data.length != attachment.getByteSize()) {
            return ResponseEntity.badRequest().build();
        }
        if (attachment.getChecksum() != null && !attachment.getChecksum().equals(md5Base64(data))) {
            return ResponseEntity.unprocessableEntity().build();
        }

        attachment.setData(data);
        attachmentRepository.save(attachment);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attachments/{token}/{fileName}")
    public ResponseEntity<byte[]> show(@PathVariable String token) {
        User currentUser = projectAccess.currentUser();
        EditorAttachment attachment = attachmentRepository.findByToken(token).orElse(null);
        if (attachment == null || attachment.getData() == null || !canView(attachment, currentUser)) {
            throw projectAccess.denied(currentUser, "attachment", token);
        }

        boolean inline = INLINE_TYPES.contains(attachment.getContentType());
        String fileName = attachment.getFileName();
        ContentDisposition.Builder builder = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
        ContentDisposition disposition = (StandardCharsets.US_ASCII.newEncoder().canEncode(fileName)
                ? builder.filename(fileName)
                : builder.filename(fileName, StandardCharsets.UTF_8)).build();

        return ResponseEntity.ok()
                .contentType(inline ? MediaType.parseMediaType(attachment.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .body(attachment.getData());
    }

    private boolean canView(EditorAttachment attachment, User user) {
        if (attachment.getProjectId() == null) {
            attachment.setProjectId(findProjectReferencing(attachment.getToken()));
            if (attachment.getProjectId() != null) {
                attachmentRepository.save(attachment);
            }
        }
        if (attachment.getProjectId() == null) {
            // Not used in any post yet: only the person who uploaded it
            return attachment.getUserId() != null && attachment.getUserId() == user.getId();
        }
        Project project = projectRepository.findById(attachment.getProjectId()).orElse(null);
        return projectAccess.isMember(project, user);
    }

    // Uploads from before attachments recorded their project: find the post that uses it
    private Long findProjectReferencing(String token) {
        // Tokens are hex, so they can't contain LIKE wildcards
        String pattern = "%" + token.replaceAll("[^0-9a-f]", "") + "%";
        for (Message message : messageRepository.findByContentLike(pattern)) {
            return message.getProjectId();
        }
        for (Comment comment : commentRepository.findByTextLike(pattern)) {
            return comment.getProjectId();
        }
        for (Todo todo : todoRepository.findByNotesLike(pattern)) {
            return todo.getProject() == null ? null : todo.getProject().getId();
        }
        return null;
    }

    private static Map<String, String> uploadHeaders(String contentType, HttpServletRequest request) {
        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Content-Type", contentType);
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            headers.put(csrf.getHeaderName(), csrf.getToken());
        }
        return headers;
    }

    // Returns null if the stream holds more than the expected number of bytes
    private static byte[] readAtMost(InputStream in, long expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(expected, Integer.MAX_VALUE));
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > expected) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String md5Base64(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String safeFileName(String name) {
        String base = name.replaceAll("[\\\\/]", "_").replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.isEmpty()) {
            base = "file";
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
