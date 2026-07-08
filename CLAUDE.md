# open-pos-analytics

open-pos の売上データを分析する3モジュール拡張: heatmap（時間帯×曜日）/ basket（併売）/ cohort（顧客RFM）。

## 技術スタック

- Backend: Kotlin / Quarkus 3.34（`backend/`）
- Frontend: TypeScript / React (Vite) + D3.js（`frontend/`）
- DB: PostgreSQL（集計テーブル事前計算、APIは読み取りのみ）

## ルール

- ~/.claude/rules/kotlin.md / typescript.md / security.md に従う
- open-pos への接続は読み取り専用。会員IDはハッシュ化して保持。カード情報は扱わない

## コマンド

```
make check     # lint → test → build（backend + frontend）
make quality   # 品質ゲート
```

詳細は各ディレクトリの Makefile と `.claude/CLAUDE.md` を参照。

## ディレクトリ構造

```
backend/   Quarkus 分析API（/api/heatmap /api/basket /api/cohort）
frontend/  React ダッシュボード
docs/adr/  設計判断（スタック選定は ADR-0001）
test/      プラグインハーネス
```

## 現在の状態

PRD.md 参照。Issue #7（CI/CD）から着手。ブランチ保護は Issue #7 完了後に設定。
