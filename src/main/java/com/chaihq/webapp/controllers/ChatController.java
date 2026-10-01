package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.Chats;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Paths;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

/**
 * Chat: each project's chat, and direct chats between people in an account (Basecamp's Campfire
 * and Pings). Opening a chat marks it read, which clears its dot.
 */
@Controller
@RequestMapping(Paths.ACCOUNT)
public class ChatController {

    private final Chats chats;
    private final ProjectAccess projectAccess;
    private final Accounts accounts;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatController(Chats chats, ProjectAccess projectAccess, Accounts accounts,
                          UserRepository userRepository, ObjectMapper objectMapper) {
        this.chats = chats;
        this.projectAccess = projectAccess;
        this.accounts = accounts;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/project/{id}/chat")
    public String projectChat(@PathVariable Long id, Model model) throws JsonProcessingException {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(id, currentUser);
        return show(chats.projectRoom(project), currentUser, model);
    }

    @GetMapping("/chats")
    public String index(Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = account(session, currentUser);
        model.addAttribute("account", account);
        model.addAttribute("chats", chats.directChats(account, currentUser));
        return "chats/index";
    }

    @GetMapping("/chats/new")
    public String newChat(Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = account(session, currentUser);
        model.addAttribute("account", account);
        model.addAttribute("people", accounts.people(account).stream()
                .map(AccountMember::getUser)
                .filter(user -> user.getId() != currentUser.getId())
                .toList());
        return "chats/new";
    }

    // Starts a chat with these people, or opens the one you already have with exactly them
    @PostMapping("/chats")
    public String start(@RequestParam(value = "people", required = false) List<Long> ids,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = account(session, currentUser);
        List<User> people = ids == null ? List.of() : userRepository.findAllById(ids).stream()
                .filter(user -> user.getId() != currentUser.getId() && accounts.role(account, user) != null)
                .toList();
        if (people.isEmpty()) {
            redirectAttributes.addFlashAttribute("destruction_notice", "Pick someone to chat with.");
            return "redirect:" + Paths.account(account) + "/chats/new";
        }
        return "redirect:" + chats.url(chats.directRoom(account, currentUser, people));
    }

    @GetMapping("/chats/{roomId}")
    public String directChat(@PathVariable long accountId, @PathVariable long roomId, Model model) throws JsonProcessingException {
        User currentUser = projectAccess.currentUser();
        ChatRoom room = chats.room(accountId, roomId, currentUser);
        if (!room.isDirect()) {
            return "redirect:" + chats.url(room);
        }
        return show(room, currentUser, model);
    }

    private String show(ChatRoom room, User currentUser, Model model) throws JsonProcessingException {
        Calendar lastReadAt = chats.markRead(room, currentUser);
        List<User> people = chats.participants(room);
        model.addAttribute("room", room);
        model.addAttribute("project", room.getProject());
        model.addAttribute("title", chats.title(room, currentUser));
        model.addAttribute("roomUrl", chats.url(room));
        model.addAttribute("people", room.isDirect() ? chats.others(room, currentUser) : people);
        List<Chat> messages = chats.messages(room, currentUser, lastReadAt);
        model.addAttribute("chatMessages", messages);
        model.addAttribute("earlierUrl", earlierUrl(room, messages));
        model.addAttribute("muted", chats.isMuted(room, currentUser));
        model.addAttribute("canModerate", !room.isDirect() && projectAccess.canManage(room.getProject(), currentUser));
        model.addAttribute("mentionable", objectMapper.writeValueAsString(chats.mentionable(room)));
        return "chat/index";
    }

    // "Load earlier messages": the page before `before`, as the same markup the chat page uses
    @GetMapping("/chats/{roomId}/messages")
    public String earlier(@PathVariable long accountId, @PathVariable long roomId, @RequestParam("before") long before, Model model) {
        User currentUser = projectAccess.currentUser();
        ChatRoom room = chats.room(accountId, roomId, currentUser);
        List<Chat> messages = chats.messagesBefore(room, currentUser, before);
        model.addAttribute("chatMessages", messages);
        model.addAttribute("earlierUrl", earlierUrl(room, messages));
        model.addAttribute("canModerate", !room.isDirect() && projectAccess.canManage(room.getProject(), currentUser));
        return "chat/messages :: list";
    }

    private String earlierUrl(ChatRoom room, List<Chat> shown) {
        return chats.hasEarlier(room, shown)
                ? Paths.account(room.getAccount()) + "/chats/" + room.getId() + "/messages?before=" + shown.get(0).getId()
                : null;
    }

    @PatchMapping(value = "/chats/{roomId}/messages/{id}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> editMessage(@PathVariable long accountId, @PathVariable long roomId, @PathVariable long id,
                                           @RequestBody Map<String, String> body) {
        User currentUser = projectAccess.currentUser();
        chats.edit(chats.room(accountId, roomId, currentUser), id, currentUser, body.get("content"));
        return Map.of("id", id);
    }

    // The page calls this when a message arrives while you're looking at the chat
    @PostMapping(value = "/chats/{roomId}/read", produces = "application/json")
    @ResponseBody
    public Map<String, Object> read(@PathVariable long accountId, @PathVariable long roomId) {
        User currentUser = projectAccess.currentUser();
        chats.markRead(chats.room(accountId, roomId, currentUser), currentUser);
        return Map.of("read", true);
    }

    // A muted chat never shows a dot; @mentions in it still reach you
    @PostMapping("/chats/{roomId}/mute")
    public String mute(@PathVariable long accountId, @PathVariable long roomId,
                       @RequestParam(value = "muted", defaultValue = "true") boolean muted, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        ChatRoom room = chats.room(accountId, roomId, currentUser);
        chats.setMuted(room, currentUser, muted);
        redirectAttributes.addFlashAttribute("notice", muted ? "Chat muted." : "Chat unmuted.");
        return "redirect:" + chats.url(room);
    }

    @DeleteMapping(value = "/chats/{roomId}/messages/{id}", produces = "application/json")
    @ResponseBody
    public Map<String, Object> deleteMessage(@PathVariable long accountId, @PathVariable long roomId, @PathVariable long id) {
        User currentUser = projectAccess.currentUser();
        chats.delete(chats.room(accountId, roomId, currentUser), id, currentUser);
        return Map.of("id", id);
    }

    // What the dots show: new in a direct chat (the nav), and which projects' chats have something new
    @GetMapping(value = "/chats/unread", produces = "application/json")
    @ResponseBody
    public Map<String, Object> unread(HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Chats.Unread unread = chats.unread(account(session, currentUser), currentUser);
        return Map.of("chats", unread.chats(), "projects", unread.projectIds());
    }

    // The sender is the signed-in user of this WebSocket session, never anyone named in the payload
    @MessageMapping("/chat.send")
    public void send(@Payload ChatMessage chatMessage, Principal principal) {
        chats.send(chatMessage, principal == null ? null : principal.getName());
    }

    // The URL's account; the session interceptor has already checked it's one of yours
    private Account account(HttpSession session, User user) {
        Account account = accounts.current(session, user);
        if (account == null) {
            throw projectAccess.denied(user, "account", null);
        }
        return account;
    }
}
