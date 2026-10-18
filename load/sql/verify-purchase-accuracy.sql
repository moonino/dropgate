WITH drop_orders AS (
    SELECT user_id, status FROM orders.orders WHERE drop_id = :'drop_id'
),
confirmed AS (
    SELECT count(*) AS confirmed_orders FROM drop_orders WHERE status = 'CONFIRMED'
),
duplicated AS (
    SELECT count(*) AS duplicated_users FROM (
        SELECT user_id FROM drop_orders GROUP BY user_id HAVING count(*) > 1
    ) AS repeated_buyers
),
ledger AS (
    SELECT count(*) AS confirmed_ledger_entries FROM orders.order_ledger WHERE drop_id = :'drop_id' AND entry_type = 'CONFIRMED'
)
SELECT
    d.total_quantity,
    d.remaining_quantity,
    d.status,
    (SELECT count(*) FROM drop_orders) AS all_orders,
    c.confirmed_orders,
    greatest(c.confirmed_orders - d.total_quantity, 0) AS oversold,
    dup.duplicated_users,
    d.total_quantity - d.remaining_quantity - c.confirmed_orders AS stock_mismatch,
    l.confirmed_ledger_entries
FROM orders.drops d, confirmed c, duplicated dup, ledger l
WHERE d.id = :'drop_id';
