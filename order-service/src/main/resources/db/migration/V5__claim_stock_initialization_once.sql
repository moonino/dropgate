ALTER TABLE orders.drops ADD COLUMN stock_initialization_claimed boolean NOT NULL DEFAULT true;
ALTER TABLE orders.drops ALTER COLUMN stock_initialization_claimed SET DEFAULT false;
