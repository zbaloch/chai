package com.chaihq.webapp.utilities;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Project;

/**
 * URLs carry the account they belong to, Basecamp-style: /{accountId}/projects, /{accountId}/project/42/...
 * so every page, link and bookmark is unambiguous even with several accounts open in different tabs.
 */
public final class Paths {

    /** Class-level prefix for controllers whose pages live inside an account. */
    public static final String ACCOUNT = "/{accountId:\\d+}";

    private Paths() {
    }

    public static String account(Account account) {
        return "/" + account.getId();
    }

    public static String account(long accountId) {
        return "/" + accountId;
    }

    /** e.g. /7/project/42 */
    public static String project(Project project) {
        return account(project.getAccount()) + "/project/" + project.getId();
    }

    public static String home(Account account) {
        return account(account) + "/projects";
    }
}
