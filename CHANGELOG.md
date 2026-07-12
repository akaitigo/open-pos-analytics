# Changelog

## v1.0.0 (2026-07-12)

MVP リリース。open-pos の売上データを3つの分析ビューに変換する。

### 機能

- **基盤** (#7, #8): CI/CD（GitHub Actions 2ジョブ + ブランチ保護）、コアデータモデル（Flyway V1-V4）、CSV 取込層（冪等・バリデーション・エラー集約）、集計再計算、10万件規模のサンプルデータ生成器
- **heatmap** (#9): `GET /api/heatmap` — 時間帯×曜日の売上/件数ヒートマップ（カテゴリ切替、繁忙/閑散TOP3、色非依存の a11y 配慮）
- **basket** (#10): `GET /api/basket/pairs` — 併売ペアの支持度/信頼度/リフト値ランキング、時間帯セグメント（朝/昼/夜）、D3 ネットワーク図、CSVエクスポート（数式インジェクション対策付き）
- **cohort** (#11): `GET /api/cohort` / `GET /api/rfm` — 月次コホートのリピート率行列、RFM 3分位セグメント（優良/離脱リスク/休眠）、会員データ無し時のフォールバック
- **admin** (#24): `POST /api/admin/ingest` / `POST /api/admin/recompute` — 取込から全集計までの一気通貫（無認証の根拠は ADR-0004）

### 修正

- 10万件取込が JTA トランザクションタイムアウト（60秒）でロールバックされる問題をチャンクコミット（500件/TX）で解消 — ship 時の実走検証で検出
- Quarkus 3.37 で無効だった `quarkus.http.cors` を `quarkus.http.cors.enabled` に修正
- Dependabot による Quarkus プラグイン更新と gradle.properties の BOM 不整合を解消

### 検証

- backend 59+ テスト / frontend 51 テスト、detekt 指摘ゼロ
- 実走検証: 100,000 トランザクション（143,131 明細）取込エラー0 → 集計4テーブル生成 → 3ビュー描画（README のスクリーンショット）
- 性能: SKU 1,000種 × 10万件のセグメント併売集計をテスト込み14.7秒（受け入れ条件 60秒）

### 設計判断

- ADR-0001: Quarkus 採用（Spring Boot 案から変更）
- ADR-0002: open-pos 連携は CSV エクスポート取込を MVP 方式とする
- ADR-0003: 会員IDは SHA-256+ソルトの一方向ハッシュのみ保持
- ADR-0004: 管理エンドポイントはローカル運用前提で無認証（本番化時は認証必須）
