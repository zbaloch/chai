package com.chaihq.webapp.utilities;

public class Constants {
    public static final String CURRENT_USER = "current_user";
    public static final String USER_STATUS_ACTIVE = "active";
    public static final String EMAIL_REGEX = "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b";

    // Account roles. Owners and admins manage the account and every project in it; members work in the projects they're on.
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";
    public static final String CURRENT_ACCOUNT = "currentAccount";
    public static final String CURRENT_ACCOUNT_ROLE = "currentAccountRole";
    public static final String PROJECT_TYPE_PROJECT = "project";

    public static final String MESSAGE = "message";
    public static final String TODO = "todo";
    public static final String COMMENTS = "comments";
    public static final String PROJECT = "project";

    public static final String NOTIFICATION_TYPE_TODO = "todo";
    public static final String NOTIFICATION_TYPE_MESSAGE = "message";
    public static final String NOTIFICATION_TYPE_MESSAGE_COMMENT = "message_comment";
    public static final String NOTIFICATION_TYPE_TODO_COMMENT = "todo_comment";
    public static final String NOTIFICATION_TYPE_FILE = "file";
    public static final String NOTIFICATION_TYPE_CHAT_MENTION = "chat_mention";

    public static final String NOTIFICATION_MESSAGE_NEW_TODO = "new_todo";
    public static final String NOTIFICATION_MESSAGE_NEW_MESSAGE = "new_message";
    public static final String NOTIFICATION_MESSAGE_NEW_MESSAGE_COMMENT = "new_message_comment";
    public static final String NOTIFICATION_MESSAGE_NEW_TODO_COMMENT = "new_todo_comment";
    public static final String NOTIFICATION_MESSAGE_NEW_FILE = "new_file";
    public static final String NOTIFICATION_MESSAGE_MENTION_MESSAGE_COMMENT = "mention_message_comment";
    public static final String NOTIFICATION_MESSAGE_MENTION_TODO_COMMENT = "mention_todo_comment";

    public static final String DELETED = "deleted";


}
