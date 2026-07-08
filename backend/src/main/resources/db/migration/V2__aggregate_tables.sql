-- 集計テーブル: バッチ再計算で全置換する（API は読み取りのみ）
-- category = '__ALL__' は全カテゴリ合算の行を表す。
-- time_segment は MVP では 'all' のみ投入（朝/昼/晩の分割は #10 で実装）。

CREATE TABLE sales_by_hour_dow (
    dow SMALLINT NOT NULL CHECK (dow BETWEEN 0 AND 6),
    hour SMALLINT NOT NULL CHECK (hour BETWEEN 0 AND 23),
    category TEXT NOT NULL DEFAULT '__ALL__',
    sales_amount NUMERIC(14, 2) NOT NULL,
    transaction_count BIGINT NOT NULL,
    item_count BIGINT NOT NULL,
    PRIMARY KEY (dow, hour, category)
);

CREATE TABLE item_pair_stats (
    product_a TEXT NOT NULL,
    product_b TEXT NOT NULL,
    time_segment TEXT NOT NULL DEFAULT 'all',
    pair_count BIGINT NOT NULL,
    support NUMERIC(10, 6) NOT NULL,
    confidence_a_to_b NUMERIC(10, 6) NOT NULL,
    confidence_b_to_a NUMERIC(10, 6) NOT NULL,
    lift NUMERIC(12, 6) NOT NULL,
    PRIMARY KEY (product_a, product_b, time_segment),
    CHECK (product_a < product_b)
);

CREATE TABLE customer_monthly_cohort (
    cohort_month DATE NOT NULL,
    activity_month DATE NOT NULL,
    active_customers BIGINT NOT NULL,
    total_sales NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (cohort_month, activity_month),
    CHECK (activity_month >= cohort_month)
);
