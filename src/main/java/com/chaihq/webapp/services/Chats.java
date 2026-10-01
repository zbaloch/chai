package com.chaihq.webapp.services;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Paths;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Project chats and direct chats between people. Whether something is new to someone is a dot,
 * never a count: a chat is unread when its latest message came after the person last looked
 * (and they haven't muted it). @mentions also go to the person's notifications.
 */
@Service
public class Chats {

    public static final int MAX_LENGTH = 10_000;
    private static final long GROUP_MILLIS = 5 * 60 * 1000;
    private static final String NOT_A_NAME = "(?<![\\p{L}\\p{N}._%+-])@(NAMES)(?![\\p{L}\\p{N}])";

    private final ChatRoomRepository roomRepository;
    private final ChatRoomReadRepository readRepository;
    private final ChatRepository chatRepository;
    private final NotificationRepository notificationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccess projectAccess;
    private final Accounts accounts;
    private final SimpMessagingTemplate messaging;

    public Chats(ChatRoomRepository roomRepository, ChatRoomReadRepository readRepository, ChatRepository chatRepository,
                 NotificationRepository notificationRepository, ProjectRepository projectRepository,
                 UserRepository userRepository, ProjectAccess projectAccess, Accounts accounts,
                 SimpMessagingTemplate messaging) {
        this.roomRepository = roomRepository;
        this.readRepository = readRepository;
        this.chatRepository = chatRepository;
        this.notificationRepository = notificationRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectAccess = projectAccess;
        this.accounts = accounts;
        this.messaging = messaging;
    }

    public record Unread(boolean chats, Set<Long> projectIds) {
    }

    /** A row on the Chats page. */
    public record Summary(ChatRoom room, String title, List<User> others, String preview, String when,
                          boolean unread, String url) {
    }

    // ---- Rooms ----

    /** A project's chat, created the first time it's needed. Its earlier messages move into it. */
    @Transactional
    public synchronized ChatRoom projectRoom(Project project) {
        return roomRepository.findFirstByProjectOrderByIdAsc(project).orElseGet(() -> {
            ChatRoom room = new ChatRoom();
            room.setAccount(project.getAccount());
            room.setProject(project);
            room.setCreatedAt(Calendar.getInstance());
            roomRepository.save(room);
            if (chatRepository.attachToRoom(project.getId(), room.getId()) > 0) {
                chatRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId())
                        .ifPresent(last -> room.setLastMessageAt(last.getCreatedAt()));
                roomRepository.save(room);
            }
            return room;
        });
    }

    /** The direct chat between exactly these people (and me), started if there isn't one yet. */
    @Transactional
    public ChatRoom directRoom(Account account, User me, Collection<User> people) {
        Set<Long> ids = new TreeSet<>();
        ids.add(me.getId());
        people.forEach(person -> ids.add(person.getId()));
        for (ChatRoom room : roomRepository.findByAccountAndProjectIsNullAndMembersContaining(account, me)) {
            if (memberIds(room).equals(ids)) {
                return room;
            }
        }
        ChatRoom room = new ChatRoom();
        room.setAccount(account);
        room.getMembers().add(me);
        people.stream().filter(person -> person.getId() != me.getId()).forEach(room.getMembers()::add);
        room.setCreatedBy(me);
        room.setCreatedAt(Calendar.getInstance());
        return roomRepository.save(room);
    }

    /** A chat the user can see, in the account from the URL. */
    public ChatRoom room(long accountId, long roomId, User user) {
        ChatRoom room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getAccount() == null || room.getAccount().getId() != accountId || !canAccess(room, user)) {
            throw projectAccess.denied(user, "chat", roomId);
        }
        return room;
    }

    /** Project chats are open to the project's people; direct chats to their members while they're in the account. */
    public boolean canAccess(ChatRoom room, User user) {
        if (room == null || user == null) {
            return false;
        }
        if (room.getProject() != null) {
            return projectAccess.isMember(room.getProject(), user);
        }
        return accounts.role(room.getAccount(), user) != null
                && room.getMembers().stream().anyMatch(member -> member.getId() == user.getId());
    }

    /** For WebSocket subscriptions, which run outside a web request. */
    @Transactional(readOnly = true)
    public boolean canAccess(Long roomId, String email) {
        ChatRoom room = roomId == null ? null : roomRepository.findById(roomId).orElse(null);
        User user = email == null ? null : userRepository.findByEmail(email);
        return canAccess(room, user);
    }

    /** Everyone in the chat: the project's people, or the direct chat's members still in the account. */
    public List<User> participants(ChatRoom room) {
        List<User> people = room.getProject() != null
                ? room.getProject().getUsers().stream().filter(user -> projectAccess.isMember(room.getProject(), user)).toList()
                : room.getMembers().stream().filter(user -> accounts.role(room.getAccount(), user) != null).toList();
        return people.stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(Chats::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** What to call a direct chat: the other person's name, or the others' first names. */
    public String title(ChatRoom room, User viewer) {
        if (room.getProject() != null) {
            return room.getProject().getName();
        }
        List<User> others = others(room, viewer);
        if (others.isEmpty()) {
            return fullName(viewer);
        }
        if (others.size() == 1) {
            return fullName(others.get(0));
        }
        return others.stream().map(User::getFirstName).collect(Collectors.joining(", "));
    }

    public List<User> others(ChatRoom room, User viewer) {
        return room.getMembers().stream()
                .filter(member -> member.getId() != viewer.getId())
                .sorted(Comparator.comparing(Chats::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public String url(ChatRoom room) {
        return room.getProject() != null
                ? Paths.project(room.getProject()) + "/chat"
                : Paths.account(room.getAccount()) + "/chats/" + room.getId();
    }

    // ---- Reading ----

    /**
     * The latest page of messages, oldest first, ready to show: formatted, grouped by person and day,
     * and with the first one that's new to the viewer marked (it gets the "New" line).
     */
    public List<Chat> messages(ChatRoom room, User viewer, Calendar lastReadAt) {
        return prepare(room, viewer, chatRepository.findTop100ByRoomIdOrderByIdDesc(room.getId()), lastReadAt, true);
    }

    /** The page of messages before this one, for "Load earlier messages". */
    public List<Chat> messagesBefore(ChatRoom room, User viewer, long beforeId) {
        return prepare(room, viewer, chatRepository.findTop100ByRoomIdAndIdLessThanOrderByIdDesc(room.getId(), beforeId), null, false);
    }

    /** Whether there are messages before this one. */
    public boolean hasEarlier(ChatRoom room, List<Chat> shown) {
        return !shown.isEmpty() && chatRepository.existsByRoomIdAndIdLessThan(room.getId(), shown.get(0).getId());
    }

    private List<Chat> prepare(ChatRoom room, User viewer, List<Chat> newestFirst, Calendar lastReadAt, boolean markNew) {
        List<Chat> messages = new ArrayList<>(newestFirst);
        Collections.reverse(messages);

        Set<Long> mentionIds = new HashSet<>();
        messages.forEach(chat -> mentionIds.addAll(ids(chat.getMentions())));
        Map<Long, User> mentioned = userRepository.findAllById(mentionIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        Calendar since = !markNew ? null
                : lastReadAt != null && (room.getCreatedAt() == null || lastReadAt.after(room.getCreatedAt()))
                ? lastReadAt : before(room.getCreatedAt());
        boolean newLinePlaced = false;
        Chat previous = null;
        for (Chat chat : messages) {
            List<Long> ids = ids(chat.getMentions());
            chat.setHtml(html(chat.getMessage(), ids.stream().map(mentioned::get).filter(Objects::nonNull).toList()));
            chat.setMentionsMe(ids.contains(viewer.getId()));
            if (previous == null || !dayKey(previous.getCreatedAt()).equals(dayKey(chat.getCreatedAt()))) {
                chat.setDayLabel(dayLabel(chat.getCreatedAt()));
            }
            if (!newLinePlaced && since != null && chat.getUser() != null && chat.getUser().getId() != viewer.getId()
                    && chat.getCreatedAt().after(since)) {
                chat.setFirstUnread(true);
                newLinePlaced = true;
            }
            chat.setContinued(previous != null && chat.getDayLabel() == null && !chat.isFirstUnread()
                    && sameSender(previous, chat)
                    && chat.getCreatedAt().getTimeInMillis() - previous.getCreatedAt().getTimeInMillis() < GROUP_MILLIS);
            previous = chat;
        }
        return messages;
    }

    /** Marks the chat read (clearing its dot and any @mentions in it). Returns when it was last read before. */
    @Transactional
    public Calendar markRead(ChatRoom room, User user) {
        ChatRoomRead read = read(room, user);
        Calendar before = read.getLastReadAt();
        read.setLastReadAt(Calendar.getInstance());
        readRepository.save(read);

        List<Notification> mentions = notificationRepository.findAllByTypeAndForUserAndReadIsFalse(Constants.NOTIFICATION_TYPE_CHAT_MENTION, user);
        if (!mentions.isEmpty()) {
            Set<Long> inRoom = chatRepository.findAllById(mentions.stream().map(Notification::getObjectId).toList()).stream()
                    .filter(chat -> Objects.equals(chat.getRoomId(), room.getId()))
                    .map(Chat::getId)
                    .collect(Collectors.toSet());
            for (Notification mention : mentions) {
                if (inRoom.contains(mention.getObjectId())) {
                    mention.setRead(true);
                    mention.setReadAt(Calendar.getInstance());
                    notificationRepository.save(mention);
                }
            }
        }
        return before;
    }

    public boolean isMuted(ChatRoom room, User user) {
        return readRepository.findFirstByRoomAndUser(room, user).map(ChatRoomRead::isMuted).orElse(false);
    }

    @Transactional
    public void setMuted(ChatRoom room, User user, boolean muted) {
        ChatRoomRead read = read(room, user);
        read.setMuted(muted);
        readRepository.save(read);
    }

    /** Which chats in the account have something new for this person. */
    @Transactional(readOnly = true)
    public Unread unread(Account account, User user) {
        if (account == null || accounts.role(account, user) == null) {
            return new Unread(false, Set.of());
        }
        List<ChatRoom> rooms = new ArrayList<>(roomRepository.findByAccountAndProjectIsNullAndMembersContaining(account, user));
        List<Project> projects = projectRepository.findDistinctByAccountAndUsersContainingOrderByNameAsc(account, user).stream()
                .filter(project -> !Constants.DELETED.equals(project.getStatus()))
                .toList();
        if (!projects.isEmpty()) {
            rooms.addAll(roomRepository.findByProjectIn(projects));
        }
        Map<Long, ChatRoomRead> reads = reads(user, rooms);

        boolean chats = false;
        Set<Long> projectIds = new TreeSet<>();
        for (ChatRoom room : rooms) {
            if (isUnread(room, reads.get(room.getId()))) {
                if (room.getProject() == null) {
                    chats = true;
                } else {
                    projectIds.add(room.getProject().getId());
                }
            }
        }
        return new Unread(chats, projectIds);
    }

    /** This person's direct chats, most recent first. */
    @Transactional(readOnly = true)
    public List<Summary> directChats(Account account, User user) {
        List<ChatRoom> rooms = roomRepository.findByAccountAndProjectIsNullAndMembersContaining(account, user);
        Map<Long, ChatRoomRead> reads = reads(user, rooms);
        return rooms.stream()
                .sorted(Comparator.comparing((ChatRoom room) -> room.getLastMessageAt() != null ? room.getLastMessageAt() : room.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(room -> {
                    Chat last = chatRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId()).orElse(null);
                    return new Summary(room, title(room, user), others(room, user), preview(last, room, user),
                            last == null ? "" : whenLabel(last.getCreatedAt()), isUnread(room, reads.get(room.getId())), url(room));
                })
                .toList();
    }

    // ---- Writing ----

    /** Posts a message sent over the WebSocket by the signed-in user. */
    @Transactional
    public void send(ChatMessage message, String email) {
        User user = email == null ? null : userRepository.findByEmail(email);
        ChatRoom room = null;
        try {
            room = roomRepository.findById(Long.parseLong(message.getRoomId())).orElse(null);
        } catch (NumberFormatException | NullPointerException ignored) {
            // not a chat
        }
        if (user == null || !canAccess(room, user) || message.getContent() == null || message.getContent().isBlank()) {
            throw projectAccess.denied(user, "chat", message.getRoomId());
        }
        post(room, user, message.getContent());
    }

    @Transactional
    public Chat post(ChatRoom room, User user, String content) {
        String text = normalize(content);
        Calendar now = Calendar.getInstance();
        List<User> people = participants(room);
        Set<Long> mentionIds = mentionedIds(text, people);

        Chat chat = new Chat();
        chat.setMessage(text);
        chat.setProjectId(room.getProject() == null ? 0 : room.getProject().getId());
        chat.setRoomId(room.getId());
        chat.setUser(user);
        chat.setCreatedAt(now);
        chat.setMentions(csv(mentionIds));
        chatRepository.save(chat);

        room.setLastMessageAt(now);
        roomRepository.save(room);
        ChatRoomRead read = read(room, user);
        read.setLastReadAt(now);
        readRepository.save(read);

        for (User person : people) {
            if (mentionIds.contains(person.getId()) && person.getId() != user.getId()) {
                notifyMention(chat, user, person, now);
            }
        }

        // Light the dot for everyone else in the chat who hasn't muted it
        Set<Long> muted = readRepository.findByRoom(room).stream()
                .filter(ChatRoomRead::isMuted)
                .map(r -> r.getUser().getId())
                .collect(Collectors.toSet());
        List<String> recipients = people.stream()
                .filter(person -> person.getId() != user.getId() && !muted.contains(person.getId()))
                .map(User::getEmail)
                .toList();

        List<User> mentioned = people.stream().filter(person -> mentionIds.contains(person.getId())).toList();
        Map<String, Object> payload = payload(chat, mentioned);
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("roomId", room.getId());
        signal.put("projectId", room.getProject() == null ? null : room.getProject().getId());
        signal.put("preview", room.isDirect() && room.getMembers().size() <= 2 ? oneLine(text) : user.getFirstName() + ": " + oneLine(text));
        signal.put("when", whenLabel(now));
        afterCommit(() -> {
            messaging.convertAndSend("/topic/chat/" + room.getId(), payload);
            recipients.forEach(email -> messaging.convertAndSendToUser(email, "/queue/chats", signal));
        });
        return chat;
    }

    /** Only the author can change what they said. Anyone newly @mentioned hears about it. */
    @Transactional
    public Chat edit(ChatRoom room, long chatId, User user, String content) {
        Chat chat = chatRepository.findById(chatId).filter(c -> Objects.equals(c.getRoomId(), room.getId())).orElse(null);
        if (chat == null || chat.getUser() == null || chat.getUser().getId() != user.getId()
                || content == null || content.isBlank()) {
            throw projectAccess.denied(user, "chat message", chatId);
        }
        String text = normalize(content);
        Calendar now = Calendar.getInstance();
        List<User> people = participants(room);
        Set<Long> alreadyMentioned = new HashSet<>(ids(chat.getMentions()));
        Set<Long> mentionIds = mentionedIds(text, people);

        chat.setMessage(text);
        chat.setMentions(csv(mentionIds));
        chat.setEditedAt(now);
        chatRepository.save(chat);
        for (User person : people) {
            if (mentionIds.contains(person.getId()) && !alreadyMentioned.contains(person.getId()) && person.getId() != user.getId()) {
                notifyMention(chat, user, person, now);
            }
        }

        List<User> mentioned = people.stream().filter(person -> mentionIds.contains(person.getId())).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "edited");
        payload.put("id", chat.getId());
        payload.put("html", html(text, mentioned));
        payload.put("mentionIds", mentioned.stream().map(User::getId).toList());
        afterCommit(() -> messaging.convertAndSend("/topic/chat/" + room.getId(), payload));
        return chat;
    }

    /** Authors can delete their own messages; account owners and admins can moderate project chats. */
    @Transactional
    public void delete(ChatRoom room, long chatId, User user) {
        Chat chat = chatRepository.findById(chatId).filter(c -> Objects.equals(c.getRoomId(), room.getId())).orElse(null);
        if (chat == null) {
            throw projectAccess.denied(user, "chat message", chatId);
        }
        boolean author = chat.getUser() != null && chat.getUser().getId() == user.getId();
        if (!author && !(room.getProject() != null && projectAccess.canManage(room.getProject(), user))) {
            throw projectAccess.denied(user, "chat message", chatId);
        }
        notificationRepository.deleteAll(notificationRepository.findAllByTypeAndObjectId(Constants.NOTIFICATION_TYPE_CHAT_MENTION, chat.getId()));
        chatRepository.delete(chat);
        chatRepository.flush();
        room.setLastMessageAt(chatRepository.findFirstByRoomIdOrderByCreatedAtDescIdDesc(room.getId())
                .map(Chat::getCreatedAt).orElse(null));
        roomRepository.save(room);
        afterCommit(() -> messaging.convertAndSend("/topic/chat/" + room.getId(), Map.of("type", "deleted", "id", chatId)));
    }

    // ---- Display ----

    /** A message as sent live to the people in the chat; the page builds it like the server-rendered ones. */
    public Map<String, Object> payload(Chat chat, List<User> mentioned) {
        User sender = chat.getUser();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message");
        payload.put("id", chat.getId());
        payload.put("roomId", chat.getRoomId());
        payload.put("senderId", sender.getId());
        payload.put("senderName", fullName(sender));
        payload.put("avatar", avatarUrl(sender));
        payload.put("html", html(chat.getMessage(), mentioned));
        payload.put("mentionIds", mentioned.stream().map(User::getId).toList());
        payload.put("createdAt", chat.getCreatedAt().getTimeInMillis());
        payload.put("time", format("h:mm a", chat.getCreatedAt()));
        payload.put("fullTime", fullTime(chat.getCreatedAt()));
        payload.put("day", dayKey(chat.getCreatedAt()));
        payload.put("dayLabel", dayLabel(chat.getCreatedAt()));
        return payload;
    }

    /** People who can be @mentioned in the chat, for the composer's suggestions. */
    public List<Map<String, Object>> mentionable(ChatRoom room) {
        return participants(room).stream().map(user -> {
            Map<String, Object> person = new LinkedHashMap<>();
            person.put("id", user.getId());
            person.put("name", fullName(user));
            person.put("firstName", Objects.toString(user.getFirstName(), ""));
            person.put("lastName", Objects.toString(user.getLastName(), ""));
            person.put("avatar", avatarUrl(user));
            return person;
        }).toList();
    }

    /** Escaped message text with links, @mentions highlighted and line breaks kept. Safe to put in the page as HTML. */
    public static String html(String text, Collection<User> mentioned) {
        if (text == null) {
            return "";
        }
        Set<String> names = new HashSet<>();
        for (User user : mentioned) {
            names.add(fullName(user));
            if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
                names.add(user.getFirstName().trim());
            }
        }
        String mentionPattern = names.isEmpty() ? "" : "|" + NOT_A_NAME.replace("NAMES", alternatives(names));
        Matcher matcher = Pattern.compile("(https?://[^\\s<>\"]+)" + mentionPattern,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);

        StringBuilder out = new StringBuilder();
        int at = 0;
        while (matcher.find()) {
            out.append(escape(text.substring(at, matcher.start())));
            if (matcher.group(1) != null) {
                String url = matcher.group(1);
                int end = url.length();
                while (end > 0 && ".,;:!?)]'".indexOf(url.charAt(end - 1)) >= 0) {
                    end--;
                }
                out.append("<a href=\"").append(escape(url.substring(0, end)))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"text-sky-700 underline decoration-sky-700/30 hover:decoration-sky-700\">")
                        .append(escape(url.substring(0, end))).append("</a>")
                        .append(escape(url.substring(end)));
            } else {
                out.append("<span class=\"rounded-sm bg-sky-50 px-0.5 font-medium text-sky-700\">")
                        .append(escape(matcher.group())).append("</span>");
            }
            at = matcher.end();
        }
        out.append(escape(text.substring(at)));
        return out.toString().replace("\n", "<br>");
    }

    /**
     * Who a message @mentions: anyone in the chat by full name ("@Ayesha Khan"), or by first name
     * when only one person in the chat has it ("@Ayesha").
     */
    public static Set<Long> mentionedIds(String text, List<User> people) {
        Map<String, Long> byName = new HashMap<>();
        Map<String, Long> firstNameCounts = people.stream()
                .filter(user -> user.getFirstName() != null && !user.getFirstName().isBlank())
                .collect(Collectors.groupingBy(user -> user.getFirstName().trim().toLowerCase(Locale.ROOT), Collectors.counting()));
        for (User user : people) {
            if (user.getFirstName() != null && firstNameCounts.getOrDefault(user.getFirstName().trim().toLowerCase(Locale.ROOT), 0L) == 1) {
                byName.put(user.getFirstName().trim().toLowerCase(Locale.ROOT), user.getId());
            }
        }
        for (User user : people) {
            if (!fullName(user).isBlank()) {
                byName.put(fullName(user).toLowerCase(Locale.ROOT), user.getId());
            }
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (byName.isEmpty() || text == null) {
            return ids;
        }
        Matcher matcher = Pattern.compile(NOT_A_NAME.replace("NAMES", alternatives(byName.keySet())),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        while (matcher.find()) {
            Long id = byName.get(matcher.group(1).toLowerCase(Locale.ROOT));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Matches "@Full Name" or "@First" for these people, or null when there's no one to match. */
    public static Pattern mentionPattern(Collection<User> mentioned) {
        Set<String> names = new HashSet<>();
        for (User user : mentioned) {
            names.add(fullName(user));
            if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
                names.add(user.getFirstName().trim());
            }
        }
        names.removeIf(String::isBlank);
        return names.isEmpty() ? null
                : Pattern.compile(NOT_A_NAME.replace("NAMES", alternatives(names)), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public static String avatarUrl(User user) {
        return "https://api.dicebear.com/10.x/initials/svg?seed="
                + URLEncoder.encode(Objects.toString(user.getFirstName(), ""), StandardCharsets.UTF_8) + "+"
                + URLEncoder.encode(Objects.toString(user.getLastName(), ""), StandardCharsets.UTF_8);
    }

    public static String fullName(User user) {
        return (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
    }

    public static String fullTime(Calendar calendar) {
        return format("MMM d, yyyy 'at' h:mm a", calendar);
    }

    /** "Today", "Yesterday", "Monday, September 29" or "March 3, 2025". */
    public static String dayLabel(Calendar calendar) {
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (dayKey(calendar).equals(dayKey(today))) {
            return "Today";
        }
        if (dayKey(calendar).equals(dayKey(yesterday))) {
            return "Yesterday";
        }
        return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                ? format("EEEE, MMMM d", calendar) : format("MMMM d, yyyy", calendar);
    }

    /** For the Chats list: "10:42 AM", "Yesterday", "Mon", "Sep 3" or "Sep 3, 2025". */
    public static String whenLabel(Calendar calendar) {
        Calendar now = Calendar.getInstance();
        String label = dayLabel(calendar);
        if (label.equals("Today")) {
            return format("h:mm a", calendar);
        }
        if (label.equals("Yesterday")) {
            return label;
        }
        if (now.getTimeInMillis() - calendar.getTimeInMillis() < 6L * 24 * 60 * 60 * 1000) {
            return format("EEE", calendar);
        }
        return calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) ? format("MMM d", calendar) : format("MMM d, yyyy", calendar);
    }

    // ---- Helpers ----

    private void notifyMention(Chat chat, User from, User to, Calendar now) {
        Notification notification = new Notification();
        notification.setType(Constants.NOTIFICATION_TYPE_CHAT_MENTION);
        notification.setMessage(Constants.NOTIFICATION_TYPE_CHAT_MENTION);
        notification.setObjectId(chat.getId());
        notification.setCreatedAt(now);
        notification.setFromUser(from);
        notification.setForUser(to);
        notificationRepository.save(notification);
    }

    private static String normalize(String content) {
        String text = content.replace("\r\n", "\n").strip();
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }

    public static String csv(Collection<Long> ids) {
        return ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static boolean isUnread(ChatRoom room, ChatRoomRead read) {
        Calendar last = room.getLastMessageAt();
        if (last == null || (read != null && read.isMuted())) {
            return false;
        }
        if (room.getCreatedAt() != null && last.before(room.getCreatedAt())) {
            return false; // only messages from before the chat had read tracking
        }
        return read == null || read.getLastReadAt() == null || last.after(read.getLastReadAt());
    }

    private ChatRoomRead read(ChatRoom room, User user) {
        return readRepository.findFirstByRoomAndUser(room, user).orElseGet(() -> {
            ChatRoomRead read = new ChatRoomRead();
            read.setRoom(room);
            read.setUser(user);
            return read;
        });
    }

    private Map<Long, ChatRoomRead> reads(User user, List<ChatRoom> rooms) {
        if (rooms.isEmpty()) {
            return Map.of();
        }
        return readRepository.findByUserAndRoomIn(user, rooms).stream()
                .collect(Collectors.toMap(read -> read.getRoom().getId(), read -> read, (a, b) -> a));
    }

    private static Set<Long> memberIds(ChatRoom room) {
        return room.getMembers().stream().map(User::getId).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String preview(Chat last, ChatRoom room, User viewer) {
        if (last == null) {
            return "No messages yet";
        }
        String text = oneLine(last.getMessage());
        if (last.getUser() != null && last.getUser().getId() == viewer.getId()) {
            return "You: " + text;
        }
        return room.getMembers().size() > 2 && last.getUser() != null ? last.getUser().getFirstName() + ": " + text : text;
    }

    private static String oneLine(String text) {
        String line = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return line.length() > 140 ? line.substring(0, 140) + "…" : line;
    }

    private static boolean sameSender(Chat a, Chat b) {
        return a.getUser() != null && b.getUser() != null && a.getUser().getId() == b.getUser().getId();
    }

    public static List<Long> ids(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }

    // Longest names first, so "@Ayesha Khan" wins over "@Ayesha"
    private static String alternatives(Collection<String> names) {
        return names.stream()
                .filter(name -> !name.isBlank())
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    private static Calendar before(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        Calendar earlier = (Calendar) calendar.clone();
        earlier.add(Calendar.MILLISECOND, -1);
        return earlier;
    }

    private static String dayKey(Calendar calendar) {
        return format("yyyy-MM-dd", calendar);
    }

    private static String format(String pattern, Calendar calendar) {
        return new SimpleDateFormat(pattern, Locale.ENGLISH).format(calendar.getTime());
    }

    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text);
    }

    // Live updates go out once the message is saved for good
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
