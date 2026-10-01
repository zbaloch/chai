package com.chaihq.webapp.services;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.ChatRepository;
import com.chaihq.webapp.repositories.ChatRoomRepository;
import com.chaihq.webapp.repositories.CommentRepository;
import com.chaihq.webapp.repositories.MessageRepository;
import com.chaihq.webapp.repositories.TodoRepository;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Paths;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Searches projects (name and description), messages, to-dos, comments and chats in the projects
 * someone can see in their current account, plus their own direct chats there.
 * Rich text is matched as plain text (so tag names never match), every word typed must appear,
 * and results come back newest first with a highlighted snippet.
 */
@Service
public class Search {

    public static final String PROJECTS = "projects", MESSAGES = "messages", TODOS = "todos", COMMENTS = "comments", CHATS = "chats";
    private static final int MAX_TERMS = 6, SNIPPET_BEFORE = 60, SNIPPET_LENGTH = 180, MAX_RESULTS = 100;

    /** A piece of snippet text; matches are shown highlighted. Rendered with th:text, never as HTML. */
    public record Segment(String text, boolean match) {}

    public record Hit(String type, String label, String title, String url, Project project,
                      User author, Calendar date, List<Segment> snippet) {
        /** The title split into plain and highlighted pieces. */
        public List<Segment> titleSegments(List<String> terms) {
            return highlight(title == null ? "" : title, terms);
        }
    }

    public record Results(List<Hit> hits, Map<String, Integer> counts) {}

    private final MessageRepository messageRepository;
    private final TodoRepository todoRepository;
    private final CommentRepository commentRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final Chats chats;

    public Search(MessageRepository messageRepository, TodoRepository todoRepository, CommentRepository commentRepository,
                  ChatRepository chatRepository, ChatRoomRepository chatRoomRepository, Chats chats) {
        this.messageRepository = messageRepository;
        this.todoRepository = todoRepository;
        this.commentRepository = commentRepository;
        this.chatRepository = chatRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chats = chats;
    }

    public static List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isBlank()).distinct().limit(MAX_TERMS).toList();
    }

    /**
     * @param projects the projects the person can see; the only ones searched
     * @param directChatsIn the account whose direct chats (the person's own) are searched too, or null for none
     */
    public Results search(String query, List<Project> projects, String type, User user, Account directChatsIn) {
        List<String> terms = terms(query);
        Map<String, Integer> counts = new LinkedHashMap<>(Map.of(PROJECTS, 0, MESSAGES, 0, TODOS, 0, COMMENTS, 0, CHATS, 0));
        if (terms.isEmpty() || (projects.isEmpty() && directChatsIn == null)) {
            return new Results(List.of(), counts);
        }

        Map<Long, Project> byId = new HashMap<>();
        projects.forEach(project -> byId.put(project.getId(), project));
        List<Long> projectIds = new ArrayList<>(byId.keySet());
        // The database narrows candidates on the longest word; every word is then checked on plain text
        String longest = terms.stream().max(Comparator.comparingInt(String::length)).orElseThrow();
        String pattern = "%" + longest.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";

        List<Hit> hits = new ArrayList<>();
        searchChats(projects, user, directChatsIn, terms, pattern, hits);
        if (projectIds.isEmpty()) {
            return results(hits, counts, type);
        }
        // Projects are already loaded (they're the ones this person can see), so match them here
        for (Project project : projects) {
            String description = project.getDescription() == null ? "" : project.getDescription();
            if (matchesAll(project.getName() + " " + description, terms)) {
                hits.add(new Hit(PROJECTS, "Project", project.getName(), Paths.project(project),
                        project, project.getUser(), project.getCreatedAt(), snippet(description, terms)));
            }
        }
        for (Message message : messageRepository.search(projectIds, pattern)) {
            String text = plain(message.getContent());
            if (matchesAll(message.getTitle() + " " + text, terms)) {
                hits.add(new Hit(MESSAGES, "Message", message.getTitle(),
                        Paths.project(byId.get(message.getProjectId())) + "/message/" + message.getId(),
                        byId.get(message.getProjectId()), message.getUser(), message.getCreatedAt(), snippet(text, terms)));
            }
        }
        for (Todo todo : todoRepository.search(projectIds, pattern)) {
            String text = plain(todo.getNotes());
            if (todo.getProject() != null && matchesAll(todo.getDescription() + " " + text, terms)) {
                hits.add(new Hit(TODOS, todo.isDone() ? "Completed to-do" : "To-do", todo.getDescription(),
                        Paths.project(byId.get(todo.getProject().getId())) + "/todo/" + todo.getId(),
                        byId.get(todo.getProject().getId()), todo.getCreatedBy(), todo.getCreatedAt(), snippet(text, terms)));
            }
        }
        for (Comment comment : commentRepository.search(projectIds, pattern)) {
            String text = plain(comment.getText());
            if (!matchesAll(text, terms)) {
                continue;
            }
            String parent, url;
            if (comment.getMessage() != null && !Constants.DELETED.equals(comment.getMessage().getStatus())) {
                parent = comment.getMessage().getTitle();
                url = Paths.project(byId.get(comment.getProjectId())) + "/message/" + comment.getMessage().getId();
            } else if (comment.getTodo() != null) {
                parent = comment.getTodo().getDescription();
                url = Paths.project(byId.get(comment.getProjectId())) + "/todo/" + comment.getTodo().getId();
            } else {
                continue; // on a deleted message
            }
            hits.add(new Hit(COMMENTS, "Comment", "Re: " + parent, url + "#comment_" + comment.getId(),
                    byId.get(comment.getProjectId()), comment.getUser(), comment.getCreatedAt(), snippet(text, terms)));
        }

        return results(hits, counts, type);
    }

    private static Results results(List<Hit> hits, Map<String, Integer> counts, String type) {
        hits.forEach(hit -> counts.merge(hit.type(), 1, Integer::sum));
        List<Hit> shown = hits.stream()
                .filter(hit -> type == null || type.isBlank() || hit.type().equals(type))
                // Projects first (they're where you're usually heading), then newest first
                .sorted(Comparator.comparing((Hit hit) -> !hit.type().equals(PROJECTS))
                        .thenComparing(Comparator.comparing((Hit hit) -> hit.date() == null ? 0L : hit.date().getTimeInMillis()).reversed()))
                .limit(MAX_RESULTS)
                .toList();
        return new Results(shown, counts);
    }

    // Project chats in these projects, and the person's own direct chats in the account
    private void searchChats(List<Project> projects, User user, Account directChatsIn, List<String> terms, String pattern, List<Hit> hits) {
        List<ChatRoom> rooms = new ArrayList<>();
        if (!projects.isEmpty()) {
            rooms.addAll(chatRoomRepository.findByProjectIn(projects));
        }
        if (directChatsIn != null) {
            rooms.addAll(chatRoomRepository.findByAccountAndProjectIsNullAndMembersContaining(directChatsIn, user));
        }
        Map<Long, ChatRoom> byId = new HashMap<>();
        rooms.stream().filter(room -> chats.canAccess(room, user)).forEach(room -> byId.put(room.getId(), room));
        if (byId.isEmpty()) {
            return;
        }
        for (Chat chat : chatRepository.search(new ArrayList<>(byId.keySet()), pattern)) {
            ChatRoom room = byId.get(chat.getRoomId());
            if (room == null || !matchesAll(chat.getMessage(), terms)) {
                continue;
            }
            String title = room.isDirect() ? "Chat with " + chats.title(room, user) : "Chat in " + room.getProject().getName();
            hits.add(new Hit(CHATS, "Chat", title, chats.url(room) + "#chat_message_" + chat.getId(),
                    room.getProject(), chat.getUser(), chat.getCreatedAt(), snippet(chat.getMessage(), terms)));
        }
    }

    private static String plain(String html) {
        return html == null ? "" : Jsoup.parse(html).text();
    }

    private static boolean matchesAll(String text, List<String> terms) {
        String lower = text.toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(lower::contains);
    }

    /** A window of text around the first match, split into plain and highlighted pieces. */
    static List<Segment> snippet(String text, List<String> terms) {
        if (text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int first = terms.stream().mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(0);
        int start = Math.max(0, first - SNIPPET_BEFORE);
        if (start > 0) {
            int space = text.indexOf(' ', start);
            start = space >= 0 && space < first ? space + 1 : start;
        }
        int end = Math.min(text.length(), start + SNIPPET_LENGTH);
        List<Segment> segments = new ArrayList<>();
        if (start > 0) segments.add(new Segment("…", false));
        segments.addAll(highlight(text.substring(start, end), terms));
        if (end < text.length()) segments.add(new Segment("…", false));
        return segments;
    }

    /** Splits text into plain and highlighted pieces, marking every occurrence of every term. */
    static List<Segment> highlight(String window, List<String> terms) {
        String windowLower = window.toLowerCase(Locale.ROOT);
        boolean[] marked = new boolean[window.length()];
        for (String term : terms) {
            for (int i = windowLower.indexOf(term); i >= 0; i = windowLower.indexOf(term, i + term.length())) {
                Arrays.fill(marked, i, i + term.length(), true);
            }
        }
        List<Segment> segments = new ArrayList<>();
        int i = 0;
        while (i < window.length()) {
            int j = i;
            while (j < window.length() && marked[j] == marked[i]) j++;
            segments.add(new Segment(window.substring(i, j), marked[i]));
            i = j;
        }
        return segments;
    }
}
