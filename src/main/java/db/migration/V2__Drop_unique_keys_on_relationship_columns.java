package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * These relationships used to be mapped as @OneToOne, so Hibernate 6 gave each join column a
 * UNIQUE key — e.g. a user could only create one project. They are many-to-one; drop the keys.
 *
 * MySQL only: on a fresh database the tables don't exist yet (Hibernate creates them after
 * Flyway runs, without these keys), and SQLite is handled by SqliteLegacyConstraintRepair.
 */
public class V2__Drop_unique_keys_on_relationship_columns extends BaseJavaMigration {

    private static final String[][] COLUMNS = {
            {"active_storage_files", "user_id"},
            {"chat_messages", "user_id"},
            {"comments", "user_id"},
            {"messages", "user_id"},
            {"notifications", "for_user"},
            {"notifications", "from_user"},
            {"projects", "created_by"},
            {"timesheets", "user_id"},
            {"timesheets", "project_id"},
            {"todos", "assigned_to"},
            {"todos", "created_by"},
            {"todos", "project_id"},
    };

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
            return;
        }

        for (String[] column : COLUMNS) {
            String table = column[0];
            String name = column[1];
            List<String> uniqueKeys = singleColumnIndexes(connection, table, name, true);
            if (uniqueKeys.isEmpty()) {
                continue;
            }

            try (Statement statement = connection.createStatement()) {
                // The foreign key on this column relies on the unique index; give it a plain one first
                if (singleColumnIndexes(connection, table, name, false).isEmpty()) {
                    statement.execute("CREATE INDEX `ix_" + table + "_" + name + "` ON `" + table + "` (`" + name + "`)");
                }
                for (String key : uniqueKeys) {
                    statement.execute("ALTER TABLE `" + table + "` DROP INDEX `" + key + "`");
                }
            }
        }
    }

    private List<String> singleColumnIndexes(Connection connection, String table, String column, boolean unique) throws SQLException {
        String sql = "SELECT INDEX_NAME FROM information_schema.STATISTICS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME <> 'PRIMARY' AND NON_UNIQUE = ?"
                + " GROUP BY INDEX_NAME HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = ?";
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setInt(2, unique ? 0 : 1);
            statement.setString(3, column);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }
}
