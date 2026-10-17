CREATE FUNCTION orders.drops_guard_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'SCHEDULED'
        AND (NEW.total_quantity <> OLD.total_quantity OR NEW.open_at <> OLD.open_at) THEN
        RAISE EXCEPTION 'OPEN 이후에는 총수량과 오픈 시각을 바꿀 수 없습니다: %', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.status <> OLD.status
        AND (OLD.status, NEW.status) NOT IN (('SCHEDULED', 'OPEN'), ('OPEN', 'SOLD_OUT'), ('OPEN', 'CLOSED'), ('SOLD_OUT', 'CLOSED')) THEN
        RAISE EXCEPTION '허용되지 않은 드롭 상태 전이입니다: % -> %', OLD.status, NEW.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER drops_guard_update
    BEFORE UPDATE ON orders.drops
    FOR EACH ROW EXECUTE FUNCTION orders.drops_guard_update();

CREATE FUNCTION orders.orders_guard_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'CONFIRMED'
        AND NEW.status = 'CANCELED'
        AND (NEW.id, NEW.drop_id, NEW.user_id, NEW.quantity, NEW.confirmed_at)
            = (OLD.id, OLD.drop_id, OLD.user_id, OLD.quantity, OLD.confirmed_at) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '주문은 CONFIRMED에서 CANCELED로 바꾸는 것만 허용합니다: %', OLD.id
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER orders_guard_update
    BEFORE UPDATE ON orders.orders
    FOR EACH ROW EXECUTE FUNCTION orders.orders_guard_update();

CREATE FUNCTION orders.orders_forbid_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '주문은 삭제할 수 없습니다'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER orders_forbid_delete
    BEFORE DELETE ON orders.orders
    FOR EACH STATEMENT EXECUTE FUNCTION orders.orders_forbid_delete();

CREATE FUNCTION orders.ledger_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '주문 원장은 바꾸거나 지울 수 없습니다: %', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER ledger_immutable
    BEFORE UPDATE OR DELETE OR TRUNCATE ON orders.order_ledger
    FOR EACH STATEMENT EXECUTE FUNCTION orders.ledger_immutable();
