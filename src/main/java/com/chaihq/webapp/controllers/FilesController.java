package com.chaihq.webapp.controllers;

import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.ActiveStorageFile;
import com.chaihq.webapp.models.Notification;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ActiveStorageFileRepository;
import com.chaihq.webapp.repositories.NotificationRepository;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.services.ProjectNotifications;
import com.chaihq.webapp.storage.StorageFileNotFoundException;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping(Paths.ACCOUNT)
public class FilesController {

    private final StorageService storageService;

    @Autowired
    public FilesController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveStorageFileRepository activeStorageFileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProjectAccess projectAccess;

    @Autowired
    private ProjectNotifications notifications;

    @GetMapping("/project/{id}/files")
    public String show(@PathVariable Long id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(id, currentUser);

        model.addAttribute("project", project);
        model.addAttribute("activeStorageFiles", activeStorageFileRepository.findAllByProjectIdOrderByCreatedAtDesc(project.getId()));

        for (Notification notification : notificationRepository.findAllByTypeAndForUser(Constants.NOTIFICATION_TYPE_FILE, currentUser)) {
            notification.setRead(true);
            notification.setReadAt(Calendar.getInstance());
            notificationRepository.save(notification);
        }

        return "files/index";
    }

    @GetMapping("/project/{projectId}/file/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable Long id, @PathVariable Long projectId) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(projectId, currentUser);
        ActiveStorageFile file = activeStorageFileRepository.findById(id).orElse(null);
        if (file == null || file.getProjectId() == null || file.getProjectId() != project.getId()) {
            throw projectAccess.denied(currentUser, "file", id);
        }

        // Always a download, never rendered: the stored content type came from the uploader
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(file.getFileData()));
    }

    @GetMapping("/project/{project_id}/file/new")
    public String neew(@ModelAttribute("activeStorageFile") ActiveStorageFile activeStorageFile,
                       @PathVariable Long project_id, Model model) {
        model.addAttribute("project", projectAccess.project(project_id, projectAccess.currentUser()));
        return "files/new";
    }

    @PostMapping("/project/{project_id}/file/new")
    public String save(@ModelAttribute("activeStorageFile") ActiveStorageFile activeStorageFile, BindingResult bindingResult,
                       @PathVariable Long project_id, Model model, RedirectAttributes redirectAttributes) throws Exception {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        model.addAttribute(Constants.PROJECT, project);

        MultipartFile upload = activeStorageFile.getMultipartFile();
        String fileName = upload == null || upload.getOriginalFilename() == null ? "" : StringUtils.cleanPath(upload.getOriginalFilename());
        if (fileName.isBlank()) {
            bindingResult.addError(new FieldError("activeStorageFile", "multipartFile", "Field not provided"));
            return "files/new";
        }

        activeStorageFile.setCreatedAt(Calendar.getInstance());
        activeStorageFile.setProjectId(project.getId());
        activeStorageFile.setUserId(currentUser.getId());
        activeStorageFile.setFileName(fileName);
        activeStorageFile.setFileType(upload.getContentType());
        activeStorageFile.setFileData(upload.getBytes());
        activeStorageFile.setFileSize(upload.getSize());
        activeStorageFileRepository.save(activeStorageFile);

        notifications.notifyProject(project, currentUser, Constants.NOTIFICATION_TYPE_FILE, activeStorageFile.getId(), Constants.NOTIFICATION_MESSAGE_NEW_FILE);

        redirectAttributes.addFlashAttribute("notice", "File uploaded!");
        return "redirect:" + Paths.project(project) + "/files";
    }

    @InitBinder("activeStorageFile")
    public void fileFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "multipartFile");
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }



}
