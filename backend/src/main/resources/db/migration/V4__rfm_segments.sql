-- RFM セグメント: 会員別の R/F/M スコアとセグメント分類（バッチ再計算で全置換）。
-- 生の会員IDは保持しない（customer_hash のみ。ADR-0003）。
-- 閾値・スコアリング・分類ルールの定義と根拠は docs/rfm-segmentation.md を参照。
-- V3 は #10（basket 時間帯別）に予約済みのため V4 を使用（Issue #11）。

CREATE TABLE rfm_segments (
    customer_hash TEXT PRIMARY KEY REFERENCES customers (customer_hash) ON DELETE CASCADE,
    recency_days INTEGER NOT NULL CHECK (recency_days >= 0),
    frequency INTEGER NOT NULL CHECK (frequency > 0),
    monetary NUMERIC(14, 2) NOT NULL CHECK (monetary >= 0),
    r_score SMALLINT NOT NULL CHECK (r_score BETWEEN 1 AND 3),
    f_score SMALLINT NOT NULL CHECK (f_score BETWEEN 1 AND 3),
    m_score SMALLINT NOT NULL CHECK (m_score BETWEEN 1 AND 3),
    segment TEXT NOT NULL CHECK (segment IN ('loyal', 'at_risk', 'dormant')),
    last_seen_at TIMESTAMPTZ NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rfm_segments_segment ON rfm_segments (segment);
