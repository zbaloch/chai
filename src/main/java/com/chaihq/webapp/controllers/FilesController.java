package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.ActiveStorageFile;
import com.chaihq.webapp.models.Notification;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ActiveStorageFileRepository;
import com.chaihq.webapp.repositories.NotificationRepository;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.storage.StorageFileNotFoundException;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
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

    @GetMapping("/project/{id}/files")
    public String show(@PathVariable Long id, Model model, HttpSession httpSession) {
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        Project project = projectRepository.getReferenceById(id); // TODO make show this belongs to the user

        List<ActiveStorageFile> activeStorageFiles = activeStorageFileRepository.findAllByProjectIdOrderByCreatedAtDesc(id);

        model.addAttribute("project", project);
        model.addAttribute("activeStorageFiles", activeStorageFiles);

        List<Notification> notifications = notificationRepository.findAllByTypeAndForUser(Constants.NOTIFICATION_TYPE_FILE, currentUser);
        for (Notification notification: notifications
        ) {
            notification.setRead(true);
            notification.setReadAt(Calendar.getInstance());
            notificationRepository.save(notification);
        }

        return "files/index";
    }

    /* @GetMapping("/project/{project_id}/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename, @PathVariable Long project_id) {

        // TODO: Make sure the file belongs to this project and the user, filenames to be stored with some project references in the name

        Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    } */

    @GetMapping("/project/{projectId}/file/{id}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable Long id, @PathVariable Long projectId) {

        System.out.println("serveFile: ");

        // TODO: Make sure the file belongs to this project and the user, filenames to be stored with some project references in the name

        System.out.println("Downloading file... " + id + "in project id " + projectId);
        ActiveStorageFile activeStorageFile = activeStorageFileRepository.getReferenceById(id);

        /* return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + activeStorageFile.getFileName() + "\"")
                .body(new ByteArrayResource(activeStorageFile.getFileData())); */

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(activeStorageFile.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + activeStorageFile.getFileName() + "\"")
                .body(new ByteArrayResource(activeStorageFile.getFileData()));

        /* Resource file = storageService.loadAsResource(filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file); */

        // return "file/"

    }


    @GetMapping("/project/{project_id}/file/new")
    public String neew(@ModelAttribute("activeStorageFile") ActiveStorageFile activeStorageFile,
                       @ModelAttribute("project")Project project, HttpSession httpSession,
                       @PathVariable Long project_id, Map<String, Object> model) {
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        project = projectRepository.getReferenceById(project_id); // TODO make show this belongs to the user
        System.out.println(project.getName());
        model.put("project", project);


        System.out.println("neew");
        return "files/new";
    }


    @PostMapping("/project/{project_id}/file/new")
    public String save(@ModelAttribute("activeStorageFile") ActiveStorageFile activeStorageFile,
                       // @RequestParam("file") MultipartFile file,
                       // @ModelAttribute("project")Project project,
                       HttpSession httpSession, @PathVariable Long project_id,
                       Map<String, Object> model, RedirectAttributes redirectAttributes,
                       BindingResult bindingResult) throws Exception {

        // storageService.store(activeStorageFile.getMultipartFile());
        Project project = projectRepository.getReferenceById(project_id); // TODO make show this belongs to the user

        model.put(Constants.PROJECT, project);

        String fileName = StringUtils.cleanPath(activeStorageFile.getMultipartFile().getOriginalFilename());

        if(fileName == null || "".equals(fileName)) {
            bindingResult.addError(new FieldError("activeStorageFile", "multipartFile", "Field not provided"));
            return "files/new";
        }


        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);

        redirectAttributes.addFlashAttribute("notice", "File uploaded!");

        activeStorageFile.setCreatedAt(Calendar.getInstance());
        activeStorageFile.setProjectId(project.getId());
        activeStorageFile.setUserId(currentUser.getId());
        activeStorageFile.setFileName(fileName);
        activeStorageFile.setFileType(activeStorageFile.getMultipartFile().getContentType());
        activeStorageFile.setFileData(activeStorageFile.getMultipartFile().getBytes());
        activeStorageFile.setFileSize(activeStorageFile.getMultipartFile().getSize());

        activeStorageFileRepository.save(activeStorageFile);

        if(currentUser.getId() != project.getUser().getId()) {
            Notification notification = new Notification();
            notification.setType(Constants.NOTIFICATION_TYPE_FILE);
            notification.setObjectId(activeStorageFile.getId());
            notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_FILE);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(currentUser);
            notification.setForUser(project.getUser());
            notificationRepository.save(notification);
        }


        for(int i=0; i<project.getUsers().size(); i++) {
            User forUser = project.getUsers().get(i);
            if(forUser.getId() != currentUser.getId()) {
                Notification notification = new Notification();
                notification.setType(Constants.NOTIFICATION_TYPE_FILE);
                notification.setObjectId(activeStorageFile.getId());
                notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_FILE);
                notification.setCreatedAt(Calendar.getInstance());
                notification.setFromUser(currentUser);
                notification.setForUser(forUser);
                notificationRepository.save(notification);
            }
        }


        // return "redirect:/projects/" + id + "/files";
        return "redirect:/project/" + project_id + "/files";
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/cloudfront")
    public ResponseEntity<String> cloudfrontSave(HttpSession httpSession) throws Exception {


        return ResponseEntity.ok()
                .header("Custom-Header", "foo")
                .body("Custom header set");
    }


}
