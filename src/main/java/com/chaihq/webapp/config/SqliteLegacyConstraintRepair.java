package com.chaihq.webapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SqliteLegacyConstraintRepair implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SqliteLegacyConstraintRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public SqliteLegacyConstraintRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isSqliteDatabase()) {
            return;
        }

        if (tableExists("todos") && hasSingleColumnUniqueIndex("todos", "project_id")) {
            logger.warn("Detected legacy UNIQUE constraint on todos.project_id. Repairing SQLite schema.");
            rebuildTodosTableWithoutUniqueProjectConstraint();
            logger.info("SQLite schema repair for todos.project_id completed.");
        }

        if (tableExists("comments") && hasSingleColumnUniqueIndex("comments", "user_id")) {
            logger.warn("Detected legacy UNIQUE constraint on comments.user_id. Repairing SQLite schema.");
            rebuildCommentsTableWithoutUniqueUserConstraint();
            logger.info("SQLite schema repair for comments.user_id completed.");
        }

        if (tableExists("active_storage_files") && hasSingleColumnUniqueIndex("active_storage_files", "user_id")) {
            logger.warn("Detected legacy UNIQUE constraint on active_storage_files.user_id. Repairing SQLite schema.");
            rebuildActiveStorageFilesTableWithoutUniqueUserConstraint();
            logger.info("SQLite schema repair for active_storage_files.user_id completed.");
        }
    }

    private boolean isSqliteDatabase() {
        try {
            String value = jdbcTemplate.queryForObject("select sqlite_version()", String.class);
            return value != null && !value.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean tableExists(String tableName) {
        String sql = "select count(*) from sqlite_master where type = 'table' and name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean hasSingleColumnUniqueIndex(String tableName, String columnName) {
        List<Map<String, Object>> indices = jdbcTemplate.queryForList("PRAGMA index_list('" + tableName + "')");

        for (Map<String, Object> index : indices) {
            Object uniqueValue = index.get("unique");
            if (!isUniqueIndex(uniqueValue)) {
                continue;
            }

            String indexName = String.valueOf(index.get("name"));
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA index_info('" + indexName + "')");

            List<String> columnNames = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                Object name = column.get("name");
                if (name != null) {
                    columnNames.add(String.valueOf(name));
                }
            }

            if (columnNames.size() == 1 && columnName.equalsIgnoreCase(columnNames.get(0))) {
                return true;
            }
        }

        return false;
    }

    private boolean isUniqueIndex(Object uniqueValue) {
        if (uniqueValue == null) {
            return false;
        }
        if (uniqueValue instanceof Number number) {
            return number.intValue() == 1;
        }
        return "1".equals(String.valueOf(uniqueValue));
    }

    private void rebuildTodosTableWithoutUniqueProjectConstraint() {
        jdbcTemplate.execute("PRAGMA foreign_keys=OFF");
        jdbcTemplate.execute("BEGIN TRANSACTION");
        try {
            jdbcTemplate.execute("ALTER TABLE todos RENAME TO todos_old");

            jdbcTemplate.execute("""
                    CREATE TABLE todos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        description VARCHAR(256) NULL,
                        assigned_to BIGINT NULL,
                        due_date TIMESTAMP NULL,
                        done BOOLEAN NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NULL,
                        project_id BIGINT NULL,
                        status VARCHAR(255) NULL,
                        notes TEXT NULL,
                        CONSTRAINT todos_users_id_fk
                            FOREIGN KEY (assigned_to) REFERENCES users (id),
                        CONSTRAINT todos_users_id_fk_2
                            FOREIGN KEY (created_by) REFERENCES users (id),
                        CONSTRAINT todos_projects_id_fk
                            FOREIGN KEY (project_id) REFERENCES projects (id)
                    )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO todos (id, description, assigned_to, due_date, done, created_by, created_at, project_id, status, notes)
                    SELECT id, description, assigned_to, due_date, done, created_by, created_at, project_id, status, notes
                    FROM todos_old
                    """);

            jdbcTemplate.execute("DROP TABLE todos_old");
            jdbcTemplate.execute("COMMIT");
        } catch (Exception ex) {
            jdbcTemplate.execute("ROLLBACK");
            throw ex;
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void rebuildCommentsTableWithoutUniqueUserConstraint() {
        jdbcTemplate.execute("PRAGMA foreign_keys=OFF");
        jdbcTemplate.execute("BEGIN TRANSACTION");
        try {
            jdbcTemplate.execute("ALTER TABLE comments RENAME TO comments_old");

            jdbcTemplate.execute("""
                    CREATE TABLE comments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        text VARCHAR(2048) NULL,
                        project_id BIGINT NULL,
                        user_id BIGINT NULL,
                        created_at TIMESTAMP NULL,
                        comment_type VARCHAR(255) NULL,
                        message_id BIGINT NULL,
                        todo_id BIGINT NULL,
                        status VARCHAR(255) NULL,
                        CONSTRAINT comments_users_id_fk
                            FOREIGN KEY (user_id) REFERENCES users (id),
                        CONSTRAINT comments_messages_id_fk
                            FOREIGN KEY (message_id) REFERENCES messages (id),
                        CONSTRAINT comments_todos_id_fk
                            FOREIGN KEY (todo_id) REFERENCES todos (id)
                    )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO comments (id, text, project_id, user_id, created_at, comment_type, message_id, todo_id, status)
                    SELECT id, text, project_id, user_id, created_at, comment_type, message_id, todo_id, status
                    FROM comments_old
                    """);

            jdbcTemplate.execute("DROP TABLE comments_old");
            jdbcTemplate.execute("COMMIT");
        } catch (Exception ex) {
            jdbcTemplate.execute("ROLLBACK");
            throw ex;
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        }
    }

    private void rebuildActiveStorageFilesTableWithoutUniqueUserConstraint() {
        jdbcTemplate.execute("PRAGMA foreign_keys=OFF");
        jdbcTemplate.execute("BEGIN TRANSACTION");
        try {
            jdbcTemplate.execute("ALTER TABLE active_storage_files RENAME TO active_storage_files_old");

            jdbcTemplate.execute("""
                    CREATE TABLE active_storage_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(256) NULL,
                        description VARCHAR(512) NULL,
                        project_id BIGINT NULL,
                        user_id BIGINT NULL,
                        created_at TIMESTAMP NULL,
                        file_data BLOB NULL,
                        file_name VARCHAR(512) NULL,
                        file_type VARCHAR(128) NULL,
                        file_size BIGINT NULL,
                        CONSTRAINT active_storage_files_users_id_fk
                            FOREIGN KEY (user_id) REFERENCES users (id)
                    )
                    """);

            jdbcTemplate.execute("""
                    INSERT INTO active_storage_files (id, name, description, project_id, user_id, created_at, file_data, file_name, file_type, file_size)
                    SELECT id, name, description, project_id, user_id, created_at, file_data, file_name, file_type, file_size
                    FROM active_storage_files_old
                    """);

            jdbcTemplate.execute("DROP TABLE active_storage_files_old");
            jdbcTemplate.execute("COMMIT");
        } catch (Exception ex) {
            jdbcTemplate.execute("ROLLBACK");
            throw ex;
        } finally {
            jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        }
    }
}
