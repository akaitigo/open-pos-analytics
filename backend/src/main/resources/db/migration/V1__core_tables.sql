-- コアテーブル: open-pos 取込データの正規化格納先（ADR-0002）
-- 会員IDはハッシュのみ保持（ADR-0003）。カード情報のカラムは設けない。

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    source_transaction_id TEXT NOT NULL UNIQUE,
    occurred_at TIMESTAMPTZ NOT NULL,
    customer_hash TEXT,
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    item_count INT NOT NULL CHECK (item_count > 0)
);

CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);
CREATE INDEX idx_transactions_customer
    ON transactions (customer_hash) WHERE customer_hash IS NOT NULL;

CREATE TABLE line_items (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    product_code TEXT NOT NULL,
    product_name TEXT NOT NULL,
    category TEXT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    line_amount NUMERIC(12, 2) NOT NULL CHECK (line_amount >= 0)
);

CREATE INDEX idx_line_items_tx ON line_items (transaction_id);
CREATE INDEX idx_line_items_product ON line_items (product_code);

CREATE TABLE customers (
    customer_hash TEXT PRIMARY KEY,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    visit_count INT NOT NULL DEFAULT 0,
    total_spent NUMERIC(14, 2) NOT NULL DEFAULT 0
);
