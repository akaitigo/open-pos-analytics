// =============================================================================
// open-pos プラグイン テストハーネス テンプレート
//
// open-pos のモック環境でプラグインの統合テストを実行する。
//
// 使い方:
//   1. {PluginName} をプラグインのクラス名に置換
//   2. テストケースをプラグインの機能に合わせて追加
//   3. `npm run test:harness` で実行
//
// 前提:
//   - @open-pos/sdk がインストール済み
//   - @open-pos/test-harness がインストール済み（devDependencies）
// =============================================================================

// TODO: open-pos SDK の実際の型定義に合わせて調整

/** open-pos プラグインの最小インターフェース */
interface PosPlugin {
  readonly name: string;
  init(context: PluginContext): Promise<void>;
  destroy(): Promise<void>;
}

/** プラグインに渡されるコンテキスト */
interface PluginContext {
  readonly sdk: PosSDK;
  readonly eventBus: EventBus;
  readonly config: Record<string, unknown>;
}

/** open-pos SDK のモック */
interface PosSDK {
  getVersion(): string;
  // TODO: 使用するSDKメソッドを追加
}

/** イベントバスのモック */
interface EventBus {
  emit(event: string, data: unknown): void;
  on(event: string, handler: (data: unknown) => void): void;
  off(event: string, handler: (data: unknown) => void): void;
}

// ---------------------------------------------------------------------------
// モック実装
// ---------------------------------------------------------------------------

function createMockSDK(): PosSDK {
  return {
    getVersion: () => "1.0.0",
  };
}

function createMockEventBus(): EventBus & { emitted: Array<{ event: string; data: unknown }> } {
  const handlers = new Map<string, Array<(data: unknown) => void>>();
  const emitted: Array<{ event: string; data: unknown }> = [];

  return {
    emitted,
    emit(event: string, data: unknown): void {
      emitted.push({ event, data });
      const eventHandlers = handlers.get(event);
      if (eventHandlers) {
        for (const handler of eventHandlers) {
          handler(data);
        }
      }
    },
    on(event: string, handler: (data: unknown) => void): void {
      const existing = handlers.get(event) ?? [];
      existing.push(handler);
      handlers.set(event, existing);
    },
    off(event: string, handler: (data: unknown) => void): void {
      const existing = handlers.get(event) ?? [];
      handlers.set(
        event,
        existing.filter((h) => h !== handler),
      );
    },
  };
}

function createMockContext(
  overrides?: Partial<PluginContext>,
): PluginContext & { eventBus: ReturnType<typeof createMockEventBus> } {
  const eventBus = createMockEventBus();
  return {
    sdk: createMockSDK(),
    eventBus,
    config: {},
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// テスト
// ---------------------------------------------------------------------------

// TODO: プロジェクトのテストフレームワーク（vitest, jest等）に合わせて調整

// import { describe, it, expect, beforeEach, afterEach } from "vitest";
// import { YourPlugin } from "../src/plugin";

/*
describe("{PluginName} テストハーネス", () => {
  let plugin: PosPlugin;
  let context: ReturnType<typeof createMockContext>;

  beforeEach(async () => {
    context = createMockContext();
    plugin = new YourPlugin();
    await plugin.init(context);
  });

  afterEach(async () => {
    await plugin.destroy();
  });

  describe("ライフサイクル", () => {
    it("init が正常に完了する", async () => {
      const freshPlugin = new YourPlugin();
      const freshContext = createMockContext();
      await expect(freshPlugin.init(freshContext)).resolves.not.toThrow();
      await freshPlugin.destroy();
    });

    it("destroy が正常に完了する", async () => {
      await expect(plugin.destroy()).resolves.not.toThrow();
    });

    it("destroy 後に再度 init できる", async () => {
      await plugin.destroy();
      const freshContext = createMockContext();
      await expect(plugin.init(freshContext)).resolves.not.toThrow();
      await plugin.destroy();
    });
  });

  describe("プラグイン名", () => {
    it("名前が定義されている", () => {
      expect(plugin.name).toBeTruthy();
      expect(typeof plugin.name).toBe("string");
    });
  });

  describe("イベントバス連携", () => {
    it("イベント名が名前空間付きである", () => {
      // プラグインが何らかの操作でイベントを発行することをトリガー
      // TODO: プラグイン固有のトリガーに置き換え

      for (const { event } of context.eventBus.emitted) {
        expect(event).toMatch(new RegExp(`^${plugin.name}:`));
      }
    });
  });

  describe("セキュリティ", () => {
    it("カード情報がイベントデータに含まれない", () => {
      // TODO: 決済関連の操作をトリガー

      for (const { data } of context.eventBus.emitted) {
        const serialized = JSON.stringify(data);
        expect(serialized).not.toMatch(/\b\d{13,19}\b/); // PAN
        expect(serialized).not.toMatch(/\b\d{3,4}\b.*cvv/i); // CVV
      }
    });
  });
});
*/

// ---------------------------------------------------------------------------
// エクスポート（テストユーティリティとして他のテストファイルから利用可能）
// ---------------------------------------------------------------------------

export { createMockSDK, createMockEventBus, createMockContext };
export type { PosPlugin, PluginContext, PosSDK, EventBus };
