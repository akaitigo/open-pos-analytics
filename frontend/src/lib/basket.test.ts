import { describe, expect, it } from "vitest";
import {
	type BasketPair,
	directedLabel,
	directedRelation,
	formatPercent,
	isBasketSegment,
	isBasketSortKey,
} from "./basket";

function pair(overrides: Partial<BasketPair> = {}): BasketPair {
	return {
		productA: "P-001",
		productB: "P-002",
		productNameA: "鮭おにぎり",
		productNameB: "緑茶500ml",
		pairCount: 130,
		support: 0.065,
		confidenceAToB: 0.65,
		confidenceBToA: 0.4,
		lift: 2.1,
		...overrides,
	};
}

describe("directedRelation", () => {
	it("信頼度が高い方向を主方向にする（A→B）", () => {
		const relation = directedRelation(pair());
		expect(relation.fromName).toBe("鮭おにぎり");
		expect(relation.toName).toBe("緑茶500ml");
		expect(relation.confidence).toBe(0.65);
	});

	it("B→A の方が高ければ逆方向を採用する", () => {
		const relation = directedRelation(pair({ confidenceAToB: 0.2, confidenceBToA: 0.7 }));
		expect(relation.fromName).toBe("緑茶500ml");
		expect(relation.toName).toBe("鮭おにぎり");
		expect(relation.confidence).toBe(0.7);
	});

	it("同値なら A→B を採用する", () => {
		const relation = directedRelation(pair({ confidenceAToB: 0.5, confidenceBToA: 0.5 }));
		expect(relation.fromName).toBe("鮭おにぎり");
	});
});

describe("directedLabel / formatPercent", () => {
	it("「A → B 65%」形式に文章化する", () => {
		expect(directedLabel(pair())).toBe("鮭おにぎり → 緑茶500ml 65%");
	});

	it("百分率は四捨五入する", () => {
		expect(formatPercent(0.654)).toBe("65%");
		expect(formatPercent(0.655)).toBe("66%");
		expect(formatPercent(0)).toBe("0%");
	});
});

describe("型ガード", () => {
	it("セグメント enum を判定する", () => {
		expect(isBasketSegment("morning")).toBe(true);
		expect(isBasketSegment("all")).toBe(true);
		expect(isBasketSegment("midnight")).toBe(false);
	});

	it("ソート軸 enum を判定する", () => {
		expect(isBasketSortKey("lift")).toBe(true);
		expect(isBasketSortKey("price")).toBe(false);
	});
});
