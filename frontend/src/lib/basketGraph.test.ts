import { describe, expect, it } from "vitest";
import type { BasketPair } from "./basket";
import { buildGraph } from "./basketGraph";

function pair(a: string, b: string, lift: number): BasketPair {
	return {
		productA: a,
		productB: b,
		productNameA: `名前${a}`,
		productNameB: `名前${b}`,
		pairCount: 10,
		support: 0.01,
		confidenceAToB: 0.5,
		confidenceBToA: 0.3,
		lift,
	};
}

describe("buildGraph", () => {
	it("重複する商品はノードを1つにまとめ、ペアごとにリンクを張る", () => {
		const graph = buildGraph([pair("P-1", "P-2", 2.0), pair("P-1", "P-3", 1.5)]);
		expect(graph.nodes.map((n) => n.id).sort()).toEqual(["P-1", "P-2", "P-3"]);
		expect(graph.links).toHaveLength(2);
		expect(graph.links[0]?.lift).toBe(2.0);
	});

	it("空配列なら空グラフ", () => {
		const graph = buildGraph([]);
		expect(graph.nodes).toHaveLength(0);
		expect(graph.links).toHaveLength(0);
	});
});
