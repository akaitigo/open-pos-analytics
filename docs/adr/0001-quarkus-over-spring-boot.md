# ADR-0001: Backend フレームワークに Quarkus を採用（Spring Boot 案を変更）

- **日付**: 2026-07-09
- **ステータス**: 承認済み
- **決定者**: Ryusei

## コンテキスト

由来アイデア（#2998/#3008/#3018）の原文と ideas-tech-reeval.json の推奨スタックはいずれも「Kotlin/Spring Boot」を提案していた。一方、連携対象の open-pos 本体および既存の拡張（pos-voice-concierge 等）は Kotlin/Quarkus で統一されている。

## 決定

Backend は **Kotlin / Quarkus 3.34**（テンプレート標準バージョン）を採用する。

## 理由

1. **エコシステム整合**: open-pos 本体・既存プラグインと同一スタックにすることで、gRPC/設定/デプロイ（Cloud Run）の知見とコードパターンを再利用できる
2. **テンプレート資産**: layer-1 kotlin テンプレート（build.gradle.kts / detekt / Makefile / hooks）が Quarkus 前提で整備済み。Spring Boot 用の品質基盤を新規整備するコストに見合う利点がない
3. **分析APIの要件**: 本プロジェクトのAPIは読み取り専用の集計配信であり、フレームワーク間の機能差は意思決定に影響しない

## 結果

- アイデア原文からの逸脱として PRD.md に明記済み
- フロントエンドも同様の理由で Next.js 案 → Vite + React（テンプレート標準）に変更。SSR 要件が生じた場合は再検討する

## 却下した選択肢

- **Spring Boot**: 生成AIの提案スタックそのままだが、エコシステム不整合のコストが利点を上回る
