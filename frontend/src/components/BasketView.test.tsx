import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { BasketPair } from "../lib/basket";
import { BasketView } from "./BasketView";
import { BasketRankingTable } from "./basket/BasketRankingTable";

const pair: BasketPair = {
	productA: "P-001",
	productB: "P-002",
	productNameA: "鮭おにぎり",
	productNameB: "緑茶500ml",
	pairCount: 130,
	support: 0.065,
	confidenceAToB: 0.65,
	confidenceBToA: 0.4,
	lift: 2.1,
};

describe("BasketView", () => {
	it("見出しと初期ロード状態を描画する（SSRではeffect未実行）", () => {
		const html = renderToString(<BasketView />);
		expect(html).toContain("併売分析");
		expect(html).toContain("読み込み中");
	});
});

describe("BasketRankingTable", () => {
	it("文章化列（A → B 65%）を含むランキング行を描画する", () => {
		const html = renderToString(<BasketRankingTable pairs={[pair]} />);
		expect(html).toContain("鮭おにぎり → 緑茶500ml 65%");
		expect(html).toContain("リフト値");
		expect(html).toContain("2.10");
	});

	it("0件なら空メッセージ", () => {
		const html = renderToString(<BasketRankingTable pairs={[]} />);
		expect(html).toContain("該当する併売ペアがありません");
	});
});
