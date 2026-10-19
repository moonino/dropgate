package dropgate.order

import org.springframework.jdbc.core.simple.JdbcClient

fun JdbcClient.withRejectedInserts(table: String, action: () -> Unit) {
    sql("CREATE FUNCTION orders.reject_insert() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
    sql("CREATE TRIGGER reject_insert BEFORE INSERT ON orders.$table FOR EACH ROW EXECUTE FUNCTION orders.reject_insert()").update()
    try {
        action()
    } finally {
        sql("DROP TRIGGER reject_insert ON orders.$table").update()
        sql("DROP FUNCTION orders.reject_insert()").update()
    }
}
