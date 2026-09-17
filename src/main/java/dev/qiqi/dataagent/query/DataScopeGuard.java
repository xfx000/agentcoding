package dev.qiqi.dataagent.query;

import dev.qiqi.dataagent.identity.UserIdentity;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds a department predicate to every SELECT block that directly reads sales_order.
 * Direct reads of sales_order_item without sales_order are rejected because they cannot be scoped.
 */
@Service
public class DataScopeGuard {
    public String apply(String safeSql, UserIdentity identity) {
        if ("ALL".equals(identity.dataScope())) return safeSql;
        if (!"DEPARTMENT".equals(identity.dataScope()) || identity.departmentId() == null) {
            throw new SecurityException("The current user has no queryable data scope");
        }
        try {
            Select select = (Select) CCJSqlParserUtil.parse(safeSql);
            ScopeVisitor visitor = new ScopeVisitor(identity.departmentId());
            visitor.getTableList((Statement) select);
            if (!visitor.violations.isEmpty()) throw new SecurityException(String.join("; ", visitor.violations));
            return select.toString();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Unable to apply department scope", e);
        }
    }

    private static final class ScopeVisitor extends TablesNamesFinder<Void> {
        private final long departmentId;
        private final List<String> violations = new ArrayList<>();

        private ScopeVisitor(long departmentId) {
            this.departmentId = departmentId;
        }

        @Override
        public <S> Void visit(PlainSelect select, S context) {
            List<TableRef> direct = directTables(select);
            boolean hasOrder = direct.stream().anyMatch(ref -> ref.table.equals("sales_order"));
            boolean hasItem = direct.stream().anyMatch(ref -> ref.table.equals("sales_order_item"));
            if (hasItem && !hasOrder) {
                violations.add("sales_order_item must be joined with sales_order in the same SELECT block");
            }
            if (hasItem && hasOrder && !hasScopedItemJoin(select, direct)) {
                violations.add("sales_order_item must join sales_order with sales_order.id = sales_order_item.order_id");
            }
            direct.stream().filter(ref -> ref.table.equals("sales_order")).forEach(ref -> {
                Expression scope = new EqualsTo(
                        new Column(ref.qualifier + ".department_id"), new LongValue(departmentId));
                select.setWhere(select.getWhere() == null ? scope : new AndExpression(select.getWhere(), scope));
            });
            return super.visit(select, context);
        }

        private static List<TableRef> directTables(PlainSelect select) {
            List<TableRef> result = new ArrayList<>();
            addTable(select.getFromItem(), result);
            if (select.getJoins() != null) {
                for (Join join : select.getJoins()) addTable(join.getRightItem(), result);
            }
            return result;
        }

        private static void addTable(FromItem item, List<TableRef> output) {
            if (!(item instanceof Table table)) return;
            String name = table.getName().toLowerCase(Locale.ROOT);
            String qualifier = table.getAlias() == null ? table.getName() : table.getAlias().getName();
            output.add(new TableRef(name, qualifier));
        }

        private static boolean hasScopedItemJoin(PlainSelect select, List<TableRef> tables) {
            if (select.getJoins() == null) return false;
            List<String> orders = tables.stream().filter(ref -> ref.table.equals("sales_order"))
                    .map(ref -> ref.qualifier.toLowerCase(Locale.ROOT)).toList();
            List<String> items = tables.stream().filter(ref -> ref.table.equals("sales_order_item"))
                    .map(ref -> ref.qualifier.toLowerCase(Locale.ROOT)).toList();
            for (Join join : select.getJoins()) {
                if (join.getOnExpressions() == null) continue;
                for (Expression on : join.getOnExpressions()) {
                    if (containsRequiredEquality(on, orders, items)) return true;
                }
            }
            return false;
        }

        private static boolean containsRequiredEquality(Expression expression, List<String> orders, List<String> items) {
            if (expression instanceof AndExpression and) {
                return containsRequiredEquality(and.getLeftExpression(), orders, items)
                        || containsRequiredEquality(and.getRightExpression(), orders, items);
            }
            if (!(expression instanceof EqualsTo equals)
                    || !(equals.getLeftExpression() instanceof Column left)
                    || !(equals.getRightExpression() instanceof Column right)) return false;
            return (isColumn(left, orders, "id") && isColumn(right, items, "order_id"))
                    || (isColumn(right, orders, "id") && isColumn(left, items, "order_id"));
        }

        private static boolean isColumn(Column column, List<String> qualifiers, String name) {
            String qualifier = column.getTable() == null ? "" : column.getTable().getName();
            return column.getColumnName().equalsIgnoreCase(name)
                    && qualifiers.contains(qualifier.toLowerCase(Locale.ROOT));
        }
    }

    private record TableRef(String table, String qualifier) {}
}
