package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.ActiveStorageFileRepository;
import com.chaihq.webapp.repositories.ChatRepository;
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

    @GetMapping("/project/{id}/chat")
    public String show(@PathVariable Long id, Model model) {
        Project project = projectRepository.getReferenceById(id); // TODO make show this belongs to the user
        List<Chat> chatMessages = chatRepository.findByProjectId(project.getId());

        model.addAttribute("project", project);
        model.addAttribute("chatMessages", chatMessages);

        return "chat/index";
    }


    /*
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        System.out.println("message: " + chatMessage.getContent() + " from " + chatMessage.getSenderUsername());
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage,
                               SimpMessageHeaderAccessor headerAccessor) {
        // Add username in web socket session
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSenderUsername());
        return chatMessage;
    }
    */

    @MessageMapping("/chat.sendMessage")
    // @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {

        System.out.println(chatMessage);
        System.out.println("message: " + chatMessage.getContent() + " from " + chatMessage.getSenderUsername());
        System.out.println("Save in the database.... "); // TODO: Need to load the messages too.
        Chat chat = new Chat();
        chat.setMessage(chatMessage.getContent());
        chat.setProjectId(Long.parseLong(chatMessage.getProjectId()));
        User user = userRepository.getReferenceById(Long.parseLong(chatMessage.getSenderId()));
        chat.setCreatedAt(Calendar.getInstance());
        chat.setUser(user);

        chatRepository.save(chat);

        this.template.convertAndSend("/topic", chatMessage);
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    // @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage,
                               SimpMessageHeaderAccessor headerAccessor) {
        // Add username in web socket session
        //headerAccessor.getSessionAttributes().put("username", chatMessage.getSenderUsername());
        this.template.convertAndSend("/topic", chatMessage);
        return chatMessage;
    }


    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/chat/{id}",  produces = "application/json")
    @ResponseBody
    public Chat deleteChatMessage(@PathVariable("project_id") long projectId, @PathVariable("id") long chatMessageId) {
        System.out.println("deleteChatMessage... ");
        Chat chat = chatRepository.getReferenceById(chatMessageId);
        chatRepository.delete(chat);
        return chat;
    }


}
