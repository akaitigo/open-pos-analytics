-- basket モジュール（#10）: 時間帯セグメント別の併売ペアランキング用インデックス。
-- item_pair_stats は time_segment ごとに 'all' + morning/noon/evening を保持する。
-- API は「time_segment で絞り込み → ソート軸で上位 N 件」を読み取るため、各ソート軸に複合インデックスを張る。
CREATE INDEX idx_item_pair_stats_seg_lift
    ON item_pair_stats (time_segment, lift DESC);

CREATE INDEX idx_item_pair_stats_seg_support
    ON item_pair_stats (time_segment, support DESC);

-- confidence ソートはペアの強い方向（A→B と B→A の大きい方）を代表値とするため式インデックスにする。
CREATE INDEX idx_item_pair_stats_seg_confidence
    ON item_pair_stats (time_segment, GREATEST(confidence_a_to_b, confidence_b_to_a) DESC);
