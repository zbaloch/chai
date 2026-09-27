package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.EditorAttachment;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.EditorAttachmentRepository;
import com.chaihq.webapp.utilities.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

    public AttachmentsController(EditorAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @PostMapping(path = "/attachments/direct_uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDirectUpload(@RequestBody Map<String, Map<String, Object>> body,
                                                HttpSession session, HttpServletRequest request) {
        User currentUser = (User) session.getAttribute(Constants.CURRENT_USER);
        Map<String, Object> blob = body.get("blob");
        if (currentUser == null || blob == null) {
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
                        "headers", Map.of("Content-Type", contentType))));
    }

    @PutMapping("/attachments/{token}/upload")
    public ResponseEntity<?> upload(@PathVariable String token, HttpSession session, HttpServletRequest request) throws IOException {
        User currentUser = (User) session.getAttribute(Constants.CURRENT_USER);
        EditorAttachment attachment = attachmentRepository.findByToken(token).orElse(null);
        if (attachment == null || currentUser == null || attachment.getUserId() != currentUser.getId()) {
            return ResponseEntity.notFound().build();
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
        EditorAttachment attachment = attachmentRepository.findByToken(token).orElse(null);
        if (attachment == null || attachment.getData() == null) {
            return ResponseEntity.notFound().build();
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
