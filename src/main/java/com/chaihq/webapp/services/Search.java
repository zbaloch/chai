package com.chaihq.webapp.services;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.CommentRepository;
import com.chaihq.webapp.repositories.MessageRepository;
import com.chaihq.webapp.repositories.TodoRepository;
import com.chaihq.webapp.utilities.Constants;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Searches messages, to-dos and comments in the projects someone can see in their current account.
 * Rich text is matched as plain text (so tag names never match), every word typed must appear,
 * and results come back newest first with a highlighted snippet.
 */
@Service
public class Search {

    public static final String MESSAGES = "messages", TODOS = "todos", COMMENTS = "comments";
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

    public Search(MessageRepository messageRepository, TodoRepository todoRepository, CommentRepository commentRepository) {
        this.messageRepository = messageRepository;
        this.todoRepository = todoRepository;
        this.commentRepository = commentRepository;
    }

    public static List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isBlank()).distinct().limit(MAX_TERMS).toList();
    }

    /** @param projects the projects the person can see; the only ones searched */
    public Results search(String query, List<Project> projects, String type) {
        List<String> terms = terms(query);
        Map<String, Integer> counts = new LinkedHashMap<>(Map.of(MESSAGES, 0, TODOS, 0, COMMENTS, 0));
        if (terms.isEmpty() || projects.isEmpty()) {
            return new Results(List.of(), counts);
        }

        Map<Long, Project> byId = new HashMap<>();
        projects.forEach(project -> byId.put(project.getId(), project));
        List<Long> projectIds = new ArrayList<>(byId.keySet());
        // The database narrows candidates on the longest word; every word is then checked on plain text
        String longest = terms.stream().max(Comparator.comparingInt(String::length)).orElseThrow();
        String pattern = "%" + longest.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";

        List<Hit> hits = new ArrayList<>();
        for (Message message : messageRepository.search(projectIds, pattern)) {
            String text = plain(message.getContent());
            if (matchesAll(message.getTitle() + " " + text, terms)) {
                hits.add(new Hit(MESSAGES, "Message", message.getTitle(),
                        "/project/" + message.getProjectId() + "/message/" + message.getId(),
                        byId.get(message.getProjectId()), message.getUser(), message.getCreatedAt(), snippet(text, terms)));
            }
        }
        for (Todo todo : todoRepository.search(projectIds, pattern)) {
            String text = plain(todo.getNotes());
            if (todo.getProject() != null && matchesAll(todo.getDescription() + " " + text, terms)) {
                hits.add(new Hit(TODOS, todo.isDone() ? "Completed to-do" : "To-do", todo.getDescription(),
                        "/project/" + todo.getProject().getId() + "/todo/" + todo.getId(),
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
                url = "/project/" + comment.getProjectId() + "/message/" + comment.getMessage().getId();
            } else if (comment.getTodo() != null) {
                parent = comment.getTodo().getDescription();
                url = "/project/" + comment.getProjectId() + "/todo/" + comment.getTodo().getId();
            } else {
                continue; // on a deleted message
            }
            hits.add(new Hit(COMMENTS, "Comment", "Re: " + parent, url + "#comment_" + comment.getId(),
                    byId.get(comment.getProjectId()), comment.getUser(), comment.getCreatedAt(), snippet(text, terms)));
        }

        hits.forEach(hit -> counts.merge(hit.type(), 1, Integer::sum));
        List<Hit> shown = hits.stream()
                .filter(hit -> type == null || type.isBlank() || hit.type().equals(type))
                .sorted(Comparator.comparing((Hit hit) -> hit.date() == null ? 0L : hit.date().getTimeInMillis()).reversed())
                .limit(MAX_RESULTS)
                .toList();
        return new Results(shown, counts);
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
