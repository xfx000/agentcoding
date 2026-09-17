package dev.qiqi.dataagent.query;

import dev.qiqi.dataagent.identity.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ReadOnlyQueryServiceTest {
    @Autowired ReadOnlyQueryService queries;
    @Autowired IdentityService identities;

    @Test
    void departmentUsersSeeDifferentAggregateFromAdmin() {
        String sql = "SELECT SUM(total_amount) AS revenue FROM sales_order WHERE status = 'PAID'";
        QueryResult admin = queries.execute(sql, identities.findActiveByUsername("admin").orElseThrow(), "admin-test");
        QueryResult alice = queries.execute(sql, identities.findActiveByUsername("alice").orElseThrow(), "alice-test");
        QueryResult bob = queries.execute(sql, identities.findActiveByUsername("bob").orElseThrow(), "bob-test");

        assertThat(amount(admin)).isEqualByComparingTo("52000.00");
        assertThat(amount(alice)).isEqualByComparingTo("27200.00");
        assertThat(amount(bob)).isEqualByComparingTo("24800.00");
        assertThat(alice.executedSql()).contains("department_id = 10");
        assertThat(bob.executedSql()).contains("department_id = 20");
        assertThat(admin.queryId()).isNotBlank();
    }

    @Test
    void lineItemAggregateMustJoinScopedOrder() {
        var alice = identities.findActiveByUsername("alice").orElseThrow();
        assertThatThrownBy(() -> queries.execute(
                "SELECT SUM(line_amount) FROM sales_order_item", alice, "unsafe-item"))
                .isInstanceOf(SecurityException.class);

        QueryResult result = queries.execute("""
                SELECT SUM(i.line_amount) AS revenue
                FROM sales_order_item i JOIN sales_order o ON o.id = i.order_id
                WHERE o.status = 'PAID'
                """, alice, "safe-item");
        assertThat(amount(result)).isEqualByComparingTo("27200.00");
    }

    @Test
    void rejectsMutationBeforeDatabaseExecution() {
        var admin = identities.findActiveByUsername("admin").orElseThrow();
        assertThatThrownBy(() -> queries.execute("DELETE FROM sales_order", admin, "mutation"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SELECT");
    }

    private static BigDecimal amount(QueryResult result) {
        Object value = result.rows().getFirst().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("revenue"))
                .map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
        return (BigDecimal) value;
    }
}
