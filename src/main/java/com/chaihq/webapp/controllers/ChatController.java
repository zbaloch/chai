package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.ActiveStorageFileRepository;
import com.chaihq.webapp.repositories.ChatRepository;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.storage.StorageFileNotFoundException;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    private final SimpMessagingTemplate template;

    private final StorageService storageService;

    @Autowired
    public ChatController(StorageService storageService, SimpMessagingTemplate template) {
        this.storageService = storageService;
        this.template = template;
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveStorageFileRepository activeStorageFileRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ProjectAccess projectAccess;

    @GetMapping("/project/{id}/chat")
    public String show(@PathVariable Long id, Model model) {
        Project project = projectAccess.project(id, projectAccess.currentUser());
        model.addAttribute("project", project);
        model.addAttribute("chatMessages", chatRepository.findByProjectId(project.getId()));
        return "chat/index";
    }

    // The sender is the signed-in user of this WebSocket session, never an id from the payload,
    // and messages only go to that project's own topic.
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        User user = principal == null ? null : userRepository.findByEmail(principal.getName());
        Project project = projectFromPayload(chatMessage);
        if (user == null || project == null || !projectAccess.isMember(project.getId(), user.getEmail())
                || chatMessage.getContent() == null || chatMessage.getContent().isBlank()) {
            throw projectAccess.denied(user, "chat for project", chatMessage.getProjectId());
        }

        Chat chat = new Chat();
        chat.setMessage(chatMessage.getContent());
        chat.setProjectId(project.getId());
        chat.setUser(user);
        chat.setCreatedAt(Calendar.getInstance());
        chatRepository.save(chat);

        chatMessage.setSenderId(String.valueOf(user.getId()));
        chatMessage.setSenderUsername(user.getEmail());
        chatMessage.setSenderFirstName(user.getFirstName());
        chatMessage.setSenderLastName(user.getLastName());
        this.template.convertAndSend("/topic/project/" + project.getId(), chatMessage);
    }

    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/chat/{id}",  produces = "application/json")
    @ResponseBody
    public Map<String, Object> deleteChatMessage(@PathVariable("project_id") long projectId, @PathVariable("id") long chatMessageId) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(projectId, currentUser);
        Chat chat = chatRepository.findById(chatMessageId).orElse(null);
        if (chat == null || chat.getProjectId() != project.getId()) {
            throw projectAccess.denied(currentUser, "chat message", chatMessageId);
        }
        projectAccess.requireAuthorOrOwner(project, chat.getUser(), currentUser, "chat message", chatMessageId);
        chatRepository.delete(chat);
        return Map.of("id", chat.getId());
    }

    private Project projectFromPayload(ChatMessage chatMessage) {
        try {
            return projectRepository.findById(Long.parseLong(chatMessage.getProjectId())).orElse(null);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
