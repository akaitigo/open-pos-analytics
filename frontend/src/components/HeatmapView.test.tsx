import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { HeatmapResponse } from "../lib/heatmap";
import { HeatmapView } from "./HeatmapView";

const sample: HeatmapResponse = {
	metric: "sales",
	category: "__ALL__",
	cells: [
		{ dow: 5, hour: 18, sales: 12340, count: 42, itemCount: 77 },
		{ dow: 1, hour: 12, sales: 680, count: 2, itemCount: 4 },
		{ dow: 2, hour: 9, sales: 100, count: 1, itemCount: 1 },
	],
};

describe("HeatmapView", () => {
	it("初期(ローディング)状態でも見出しとカテゴリ切替を描画する", () => {
		const html = renderToString(<HeatmapView />);
		expect(html).toContain("時間帯×曜日");
		expect(html).toContain("全カテゴリ");
		expect(html).toContain("読み込み中");
	});

	it("initialData を与えるとヒートマップと繁忙/閑散サマリーを描画する", () => {
		const html = renderToString(<HeatmapView initialData={sample} initialCategories={["米飯", "飲料"]} />);
		expect(html).toContain("<svg");
		expect(html).toContain("繁忙 TOP3");
		expect(html).toContain("閑散 TOP3");
		// 繁忙1位は金曜18時 ¥12,340
		expect(html).toContain("金曜 18時 — ¥12,340（42件）");
		// セル内の短縮値ラベル
		expect(html).toContain("12.3k");
		// カテゴリ選択肢が反映される
		expect(html).toContain("米飯");
	});

	it("metric=count の initialData では取引数指標で描画する", () => {
		const countData: HeatmapResponse = { ...sample, metric: "count" };
		const html = renderToString(<HeatmapView initialData={countData} />);
		expect(html).toContain("42件");
	});
});
