WITH drop_orders AS (
    SELECT id, user_id, status FROM orders.orders WHERE drop_id = :'drop_id'
),
order_counts AS (
    SELECT
        count(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed,
        count(*) FILTER (WHERE status = 'CANCELED') AS canceled
    FROM drop_orders
),
duplicated AS (
    SELECT count(*) AS users
    FROM (
        SELECT user_id FROM drop_orders GROUP BY user_id HAVING count(*) > 1
    ) repeated_buyers
),
outbox_counts AS (
    SELECT
        count(*) FILTER (WHERE status = 'PENDING') AS pending,
        count(*) FILTER (WHERE status = 'PUBLISHED') AS published,
        count(*) FILTER (WHERE status = 'FAILED') AS failed
    FROM orders.outbox
    WHERE aggregate_type = 'order' AND aggregate_id IN (SELECT id FROM drop_orders)
)
SELECT json_build_object(
    'dropId', d.id,
    'totalQuantity', d.total_quantity,
    'dbRemainingQuantity', d.remaining_quantity,
    'status', d.status,
    'confirmed', c.confirmed,
    'canceled', c.canceled,
    'duplicatedUsers', dup.users,
    'outbox', json_build_object('pending', o.pending, 'published', o.published, 'failed', o.failed)
)
FROM orders.drops d CROSS JOIN order_counts c CROSS JOIN outbox_counts o CROSS JOIN duplicated dup
WHERE d.id = :'drop_id';
