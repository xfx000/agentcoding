package dev.qiqi.dataagent.query;

import dev.qiqi.dataagent.config.QiqiProperties;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SqlPolicy {
    private static final Set<String> BLOCKED_FUNCTIONS = Set.of(
            "sleep", "benchmark", "load_file", "get_lock", "release_lock",
            "pg_sleep", "pg_read_file", "pg_read_binary_file", "pg_ls_dir",
            "dblink_connect", "dblink_exec", "lo_export", "csvread", "csvwrite",
            "sys_exec", "sys_eval", "file_read", "file_write");
    private static final Pattern BLOCKED_TEXT = Pattern.compile(
            "(?is)(?:\\binto\\s+(?:outfile|dumpfile)\\b|--|/\\*|#|\\bfor\\s+update\\b)");

    private final int maxRows;
    private final Set<String> exposedTables;

    public SqlPolicy(QiqiProperties properties) {
        this.maxRows = properties.query().maxRows();
        this.exposedTables = Set.copyOf(properties.exposedTables());
    }

    public SqlValidation validate(String sql) {
        if (sql == null || sql.isBlank()) return SqlValidation.reject("SQL is blank");
        String normalized = stripTrailingSemicolons(sql.trim());
        if (BLOCKED_TEXT.matcher(normalized).find()) {
            return SqlValidation.reject("SQL contains a blocked locking, comment, or file-output construct");
        }
        try {
            Statements parsed = CCJSqlParserUtil.parseStatements(normalized);
            if (parsed.size() != 1) return SqlValidation.reject("Only one SQL statement is allowed");
            Statement statement = parsed.get(0);
            if (!(statement instanceof Select select)) return SqlValidation.reject("Only SELECT or WITH queries are allowed");

            DangerousFunctionVisitor functions = new DangerousFunctionVisitor();
            String blockedFunction = functions.find(statement);
            if (blockedFunction != null) return SqlValidation.reject("Blocked function: " + blockedFunction);

            Set<String> tables = new HashSet<>();
            for (String table : new TablesNamesFinder<Void>().getTableList(statement)) {
                String normalizedTable = normalizeTable(table);
                if (normalizedTable.contains(".")) {
                    return SqlValidation.reject("Cross-schema table references are not allowed: " + table);
                }
                String simple = normalizedTable;
                if (!exposedTables.contains(simple)) {
                    return SqlValidation.reject("Table is not exposed to the agent: " + table);
                }
                tables.add(simple);
            }
            if (tables.isEmpty()) return SqlValidation.reject("A query must reference at least one exposed table");

            Limit limit = select.getLimit();
            if (limit == null) {
                limit = new Limit();
                limit.setRowCount(new net.sf.jsqlparser.expression.LongValue(maxRows));
                select.setLimit(limit);
            } else if (limit.getRowCount() instanceof net.sf.jsqlparser.expression.LongValue value
                    && value.getValue() > maxRows) {
                value.setValue(maxRows);
            }
            return SqlValidation.allow(select.toString());
        } catch (Exception e) {
            return SqlValidation.reject("SQL parse failed: " + oneLine(e.getMessage()));
        }
    }

    private static String stripTrailingSemicolons(String sql) {
        int end = sql.length();
        while (end > 0 && (sql.charAt(end - 1) == ';' || Character.isWhitespace(sql.charAt(end - 1)))) end--;
        return sql.substring(0, end);
    }

    private static String normalizeTable(String table) {
        return table.toLowerCase(Locale.ROOT).replace("`", "").replace("\"", "");
    }

    private static String oneLine(String value) {
        if (value == null) return "unknown error";
        String compact = value.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }

    private static final class DangerousFunctionVisitor extends TablesNamesFinder<Void> {
        private String found;

        String find(Statement statement) {
            found = null;
            getTableList(statement);
            return found;
        }

        @Override
        public <S> Void visit(Function function, S context) {
            if (found == null && function.getName() != null) {
                String candidate = function.getName().toLowerCase(Locale.ROOT);
                if (BLOCKED_FUNCTIONS.contains(candidate)) found = candidate;
            }
            return super.visit(function, context);
        }
    }
}
