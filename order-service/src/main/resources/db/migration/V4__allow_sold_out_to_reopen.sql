CREATE OR REPLACE FUNCTION orders.drops_guard_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'SCHEDULED'
        AND (NEW.total_quantity <> OLD.total_quantity OR NEW.open_at <> OLD.open_at) THEN
        RAISE EXCEPTION 'OPEN 이후에는 총수량과 오픈 시각을 바꿀 수 없습니다: %', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF NEW.status <> OLD.status
        AND (OLD.status, NEW.status) NOT IN (('SCHEDULED', 'OPEN'), ('OPEN', 'SOLD_OUT'), ('OPEN', 'CLOSED'), ('SOLD_OUT', 'CLOSED'), ('SOLD_OUT', 'OPEN')) THEN
        RAISE EXCEPTION '허용되지 않은 드롭 상태 전이입니다: % -> %', OLD.status, NEW.status
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;
