package dev.qiqi.dataagent.query;

import dev.qiqi.dataagent.identity.UserIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DataScopeGuardTest {
    private final DataScopeGuard guard = new DataScopeGuard();
    private final UserIdentity north = new UserIdentity(2, "alice", "Alice", "DEPARTMENT", 10L);

    @Test
    void addsScopeToDirectAndNestedOrderReads() {
        String direct = guard.apply("SELECT SUM(total_amount) FROM sales_order WHERE status = 'PAID' LIMIT 200", north);
        assertThat(direct).contains("sales_order.department_id = 10");

        String nested = guard.apply("""
                WITH x AS (SELECT id, total_amount FROM sales_order)
                SELECT SUM(total_amount) FROM x LIMIT 200
                """, north);
        assertThat(nested).contains("sales_order.department_id = 10");
    }

    @Test
    void usesAliasAndScopesLineItemJoin() {
        String sql = guard.apply("""
                SELECT SUM(i.line_amount)
                FROM sales_order_item i JOIN sales_order o ON o.id = i.order_id
                LIMIT 200
                """, north);
        assertThat(sql).contains("o.department_id = 10");
    }

    @Test
    void rejectsUnscopedLineItemsAndInvalidIdentity() {
        assertThatThrownBy(() -> guard.apply("SELECT SUM(line_amount) FROM sales_order_item LIMIT 200", north))
                .isInstanceOf(SecurityException.class).hasMessageContaining("must be joined");
        assertThatThrownBy(() -> guard.apply("SELECT * FROM sales_order LIMIT 200",
                new UserIdentity(9, "none", "None", "DEPARTMENT", null)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.apply("""
                SELECT SUM(i.line_amount) FROM sales_order_item i
                JOIN sales_order o ON 1 = 1 LIMIT 200
                """, north)).isInstanceOf(SecurityException.class).hasMessageContaining("order_id");
        assertThatThrownBy(() -> guard.apply("""
                SELECT SUM(i.line_amount) FROM sales_order_item i
                JOIN sales_order o ON o.id = i.order_id OR 1 = 1 LIMIT 200
                """, north)).isInstanceOf(SecurityException.class).hasMessageContaining("order_id");
    }

    @Test
    void allScopeLeavesSqlUntouched() {
        String sql = "SELECT * FROM sales_order LIMIT 200";
        assertThat(guard.apply(sql, new UserIdentity(1, "admin", "Admin", "ALL", null))).isEqualTo(sql);
    }
}
