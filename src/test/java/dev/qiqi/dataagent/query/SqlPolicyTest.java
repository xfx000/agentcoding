package dev.qiqi.dataagent.query;

import dev.qiqi.dataagent.config.QiqiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlPolicyTest {
    private SqlPolicy policy;

    @BeforeEach
    void setUp() {
        QiqiProperties properties = new QiqiProperties(null,
                new QiqiProperties.Query(50, Duration.ofSeconds(5)),
                Set.of("sales_order", "sales_order_item", "customer", "product", "department"));
        policy = new SqlPolicy(properties);
    }

    @Test
    void addsAndCapsLimit() {
        assertThat(policy.validate("SELECT * FROM sales_order").safeSql()).endsWith("LIMIT 50");
        assertThat(policy.validate("SELECT * FROM sales_order LIMIT 999").safeSql()).endsWith("LIMIT 50");
        assertThat(policy.validate("SELECT * FROM sales_order LIMIT 10").safeSql()).endsWith("LIMIT 10");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM sales_order",
            "SELECT * FROM sales_order; DROP TABLE sales_order",
            "SELECT SLEEP(10) FROM sales_order",
            "SELECT IF(1=1, SLEEP(10), 0) FROM sales_order",
            "WITH x AS (SELECT GET_LOCK('x', 10) FROM sales_order) SELECT * FROM x",
            "SELECT PG_SLEEP(10) FROM sales_order",
            "SELECT PG_READ_FILE('/etc/passwd') FROM sales_order",
            "SELECT * FROM app_user",
            "SELECT * FROM other_schema.sales_order",
            "SELECT 1",
            "SELECT * FROM sales_order FOR UPDATE",
            "SELECT * FROM sales_order -- bypass"
    })
    void rejectsUnsafeOrUnexposedQueries(String sql) {
        SqlValidation result = policy.validate(sql);
        assertThat(result.valid()).as(result.reason()).isFalse();
        assertThat(result.safeSql()).isNull();
    }

    @Test
    void acceptsJoinAndCteAcrossExposedTables() {
        assertThat(policy.validate("""
                WITH paid AS (SELECT id, customer_id FROM sales_order WHERE status = 'PAID')
                SELECT c.name, COUNT(*) FROM paid p JOIN customer c ON c.id = p.customer_id GROUP BY c.name
                """).valid()).isTrue();
    }
}
