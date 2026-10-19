package dropgate.order

import org.springframework.jdbc.core.simple.JdbcClient

fun JdbcClient.withRejectedWrites(table: String, action: () -> Unit) {
    sql("CREATE FUNCTION orders.reject_write() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test failure''; END'").update()
    sql("CREATE TRIGGER reject_write BEFORE INSERT ON orders.$table FOR EACH ROW EXECUTE FUNCTION orders.reject_write()").update()
    try {
        action()
    } finally {
        sql("DROP TRIGGER reject_write ON orders.$table").update()
        sql("DROP FUNCTION orders.reject_write()").update()
    }
}
