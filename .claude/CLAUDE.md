# アーキテクチャ概要

```
open-pos (PostgreSQL) --読取専用--> ingest --正規化--> analytics DB
                                                  |
                 backend (Quarkus): HeatmapResource / BasketResource / CohortResource
                                                  |
                 frontend (React+D3): Heatmap / Basket / Cohort ビュー
```

## 設計判断（ADR）

- ADR-0001（予定）: Backend は Spring Boot 案（アイデア原文）ではなく Quarkus を採用 — open-pos 本体・pos-voice-concierge との整合、既存テンプレート資産
- ADR-0002（予定）: open-pos データ接続方式（リードレプリカ / エクスポートAPI / CSV取込）— Issue #2 で決定
- ADR-0003（承認済み）: 会員IDは SHA-256 + 環境変数ソルトでハッシュ化し生値を保持しない

## モジュール構成

| モジュール | 由来アイデア | 集計テーブル | API |
|-----------|-------------|-------------|-----|
| heatmap | #2998 (30点/Tier S) | sales_by_hour_dow(_category) | GET /api/heatmap |
| basket | #3008 (27点) | item_pair_stats（支持度/信頼度/リフト） | GET /api/basket/pairs |
| cohort | #3018 (25点) | customer_monthly_cohort, rfm_segments | GET /api/cohort, /api/rfm |

## 外部サービス連携

- open-pos PostgreSQL（読み取り専用ロール。スキーマ依存は ingest に閉じ込める）
- GCP Cloud Run（Ship 段階でコンテナ化）

## 制約

- 分析はバッチ事前集計。リアルタイム性は要求しない
- カード情報・生の会員IDを分析DBに保存しない（pos-plugin セキュリティチェックが Stop hook で強制）
