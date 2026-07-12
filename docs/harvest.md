# Harvest: open-pos-analytics

> Stage 6 (Harvest)。v1.0.0 ship完了（2026-07-12）後の振り返り。

## メトリクス

| 項目 | 値 |
|------|-----|
| Issue (closed/total) | 7/7 (100%) |
| PR merged | 18 |
| テスト数 | backend 77 / frontend 51（計128） |
| CI失敗数 | 10/100（直近100run中。うち大半はDependabot major更新の相互依存衝突・biome設定非互換によるnpm ci/lint失敗。#18対応で解消済み） |
| ADR数 | 4 |
| コミット数 (non-merge) | 21 |
| CLAUDE.md行数 | ルート36行 + .claude/33行（各50行以下） |

## ハーネス適用状況

### Layer-0 (リポジトリ衛生)
- [x] CLAUDE.md（50行以下）
- [x] Makefile（check/quality ターゲット）
- [x] LICENSE
- [x] .gitignore
- [ ] .claudeignore（未導入）
- [x] lefthook.yml
- [x] startup.sh（実体あり）
- [x] ADR 4件
- [x] dependabot.yml

### Layer-1 (ツール強制)
- [x] .claude/settings.json
- [x] CI（.github/workflows/ci.yml、backend/frontend 2ジョブ）
- [x] lint設定（detekt.yml / biome.json / oxlint）
- [x] format設定（kotlinter + biome format、#17でmake format実効化）
- [x] 型チェック（tsc --noEmit）

### Layer-2 (プロセス)
- [x] Issue-PR 1:1対応（Issue 7件、対応PRを個別に確認可能）
- [x] model ラベル分類（haiku/sonnet/opus、Issue#7-11/17/18全件付与）
- [ ] PRD 受け入れ条件が全 [x] — 「未解決事項」3件中2件（データ接続方式・会員IDハッシュ方式）はADR-0002/0003で実質決着済みだがPRD.mdのチェックボックスが未更新のまま。3件目（競合の最新状況調査）は未着手

## テンプレートへの改善提案

| # | 優先度 | 対象ファイル | 変更内容 | 根拠 |
|---|--------|-------------|---------|------|
| 1 | 高 | idea-launch SKILL.md / Dependabotテンプレート | major更新Issueの受け入れ条件に「rebaseしてもCIが通らない場合、peer依存の相互依存を疑い個別PRを統合ブランチに束ねる」手順を明記 | #18でplugin-react⇔vite、vitest⇔@vitest/coverage-v8が相互peer依存で単独PRでは`npm ci`が失敗した。個別評価を前提にした受け入れ条件だと想定外の詰まりになる |
| 2 | 高 | idea-launch SKILL.md | biomejs major更新（1.x→2.x）を検知したら`biome migrate --write`をIssue手順に組み込む | 既にIssue #18本文に記載済みだったが、実行時にfolder ignore記法・import順の追加fixが必要だった。migrate単体では安全fixが100%適用されないケースがある旨を追記 |
| 3 | 中 | idea-work SKILL.md | 依存bump系PRを部分適用した際は`package.json`変更を`git add`する時に対応する`package-lock.json`も同一コミットに含める確認ステップを追加 | 本セッションでpackage.jsonのみgit addしてpackage-lock.jsonのroot engines反映漏れが発生（#29で別途修正） |
| 4 | 低 | PRDテンプレート | 「未解決事項」チェックボックスをADR作成時に自動的にチェック済みへ倒す運用ルールを明記（もしくはADRへのリンクを併記） | PRD.mdの未解決事項2件がADRで実質決着済みなのに文書上は未チェックのまま放置されていた |
| 5 | 低 | layer-0 CLAUDE.md 生成テンプレート | .claudeignore の生成を必須化する | 本リポジトリは.gitignoreのみで.claudeignore未導入 |
| 6 | 中 | idea-harvest SKILL.md | ADR数の収集コマンドを `ls docs/adr/*.md \| wc -l` から `ls docs/adr/[0-9]*.md \| wc -l` に変更（adr-template.mdを除外） | 本harvest作成時に `adr-template.md` を誤ってADR件数に含めてしまい、Codexレビューで実測4件との不一致を指摘された |

## 振り返り

### 良かった点
- MVP全Issue（#7-#11）+ admin取込 + JTAタイムアウト修正まで、実走検証（10万件取込）込みでv1.0.0まで完走
- kotlinter導入（#17）は意味的変更混入ゼロを整形冪等性チェック・detekt再実行・テストで裏取りしてマージでき、整形系PRのレビュー基準が確立できた
- Dependabot major更新（#18）で相互依存の衝突を検出し、個別PRのまま強行せず統合PRに束ねる判断ができた（npm audit: critical 2件→0件まで改善）

### 改善点
- Dependabot個別PRの受け入れ条件が「1PR=1判断」前提だったため、相互peer依存のあるフロントエンドツールチェーン（vite/plugin-react/vitest/coverage-v8）の組み合わせ検証に想定より時間がかかった
- package.json変更時にpackage-lock.jsonの反映漏れが発生（#29で追加修正）。コミット前のstaged diff確認が甘かった
- PRD.mdの未解決事項がADR決定後も更新されず、ドキュメントの一次情報源としての信頼度が下がっていた

### 次のPJへの申し送り
- フロントエンドのメジャー依存更新は、着手前に`npm info <pkg> peerDependencies`で関連パッケージとの相互依存を確認してから個別Issue化するとやり直しが減る
- lockfile付き依存更新コミットは`git status`でstaged/unstagedの両方にpackage-lock.jsonが含まれているか必ず確認してからcommitする
- ADRで決着した論点はPRDの該当チェックボックスも同じコミットで更新する
