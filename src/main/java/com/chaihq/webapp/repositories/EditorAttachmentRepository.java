package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.EditorAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EditorAttachmentRepository extends JpaRepository<EditorAttachment, Long> {
    Optional<EditorAttachment> findByToken(String token);
}
