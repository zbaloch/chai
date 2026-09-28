package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Introduces accounts (companies) between people and projects, and moves existing data into them:
 *
 *  - everyone who created a project gets an account ("Zaheer's company"), which they own;
 *  - their projects move into that account;
 *  - everyone on those projects becomes a member of that account;
 *  - people who never created a project get no account of their own — they're members of the
 *    accounts they already work in;
 *  - project creators are added to their projects' member lists (projects no longer have owners);
 *  - "team" and "hq" projects become ordinary projects.
 *
 * Works on MySQL and SQLite. On a brand-new database there's nothing to migrate: Hibernate
 * creates all tables after Flyway runs.
 */
public class V3__Create_accounts extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "projects")) {
            return;
        }
        boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");

        try (Statement statement = connection.createStatement()) {
            if (mysql) {
                statement.execute("CREATE TABLE IF NOT EXISTS accounts (id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(255), "
                        + "created_at DATETIME(6), created_by BIGINT, PRIMARY KEY (id)) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS account_users (id BIGINT NOT NULL AUTO_INCREMENT, account_id BIGINT, "
                        + "user_id BIGINT, role VARCHAR(255), created_at DATETIME(6), PRIMARY KEY (id)) ENGINE=InnoDB");
            } else {
                statement.execute("CREATE TABLE IF NOT EXISTS accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR(255), "
                        + "created_at DATETIME, created_by BIGINT)");
                statement.execute("CREATE TABLE IF NOT EXISTS account_users (id INTEGER PRIMARY KEY AUTOINCREMENT, account_id BIGINT, "
                        + "user_id BIGINT, role VARCHAR(255), created_at DATETIME)");
            }
            if (!columnExists(connection, "projects", "account_id")) {
                statement.execute("ALTER TABLE projects ADD COLUMN account_id BIGINT");
            }

            statement.executeUpdate("UPDATE projects SET project_type = 'project' WHERE project_type IS NULL OR project_type <> 'project'");

            // Project creators become members of their projects
            statement.executeUpdate("INSERT INTO project_users (project_id, user_id) "
                    + "SELECT p.id, p.created_by FROM projects p WHERE p.created_by IS NOT NULL AND NOT EXISTS "
                    + "(SELECT 1 FROM project_users pu WHERE pu.project_id = p.id AND pu.user_id = p.created_by)");
        }

        for (long ownerId : projectCreatorsWithoutAccount(connection)) {
            long accountId = createAccount(connection, ownerId);

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE projects SET account_id = ? WHERE created_by = ? AND account_id IS NULL")) {
                update.setLong(1, accountId);
                update.setLong(2, ownerId);
                update.executeUpdate();
            }
            addPerson(connection, accountId, ownerId, "owner");

            List<Long> members = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT DISTINCT pu.user_id FROM project_users pu JOIN projects p ON p.id = pu.project_id "
                            + "WHERE p.account_id = ? AND pu.user_id <> ?")) {
                select.setLong(1, accountId);
                select.setLong(2, ownerId);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        members.add(rs.getLong(1));
                    }
                }
            }
            for (long memberId : members) {
                addPerson(connection, accountId, memberId, "member");
            }
        }
    }

    private List<Long> projectCreatorsWithoutAccount(Connection connection) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT DISTINCT p.created_by FROM projects p "
                     + "JOIN users u ON u.id = p.created_by WHERE p.account_id IS NULL ORDER BY p.created_by")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private long createAccount(Connection connection, long ownerId) throws SQLException {
        String firstName = null;
        try (PreparedStatement select = connection.prepareStatement("SELECT first_name FROM users WHERE id = ?")) {
            select.setLong(1, ownerId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    firstName = rs.getString(1);
                }
            }
        }
        String name = firstName == null || firstName.isBlank() ? "My company" : firstName.trim() + "'s company";

        // Dated like the owner's first project, stored in whatever format this database uses for it
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO accounts (name, created_by, created_at) "
                        + "SELECT ?, ?, MIN(created_at) FROM projects WHERE created_by = ?")) {
            insert.setString(1, name);
            insert.setLong(2, ownerId);
            insert.setLong(3, ownerId);
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement("SELECT MAX(id) FROM accounts WHERE created_by = ?")) {
            select.setLong(1, ownerId);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void addPerson(Connection connection, long accountId, long userId, String role) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO account_users (account_id, user_id, role, created_at) "
                        + "SELECT ?, ?, ?, MIN(created_at) FROM projects WHERE account_id = ?")) {
            insert.setLong(1, accountId);
            insert.setLong(2, userId);
            insert.setString(3, role);
            insert.setLong(4, accountId);
            insert.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }
}
