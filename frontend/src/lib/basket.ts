import { fetchJson } from "./api";

export type BasketSegment = "morning" | "noon" | "evening" | "all";
export type BasketSortKey = "lift" | "confidence" | "support";

export interface BasketPair {
	productA: string;
	productB: string;
	productNameA: string;
	productNameB: string;
	pairCount: number;
	support: number;
	confidenceAToB: number;
	confidenceBToA: number;
	lift: number;
}

export interface BasketPairsResponse {
	segment: string;
	sort: string;
	limit: number;
	count: number;
	pairs: BasketPair[];
}

export interface BasketQuery {
	segment: BasketSegment;
	sort: BasketSortKey;
	limit: number;
}

export const SEGMENT_LABELS: Record<BasketSegment, string> = {
	all: "終日",
	morning: "朝 (5-10時)",
	noon: "昼 (11-16時)",
	evening: "夜 (17-翌4時)",
};

export const SORT_LABELS: Record<BasketSortKey, string> = {
	lift: "リフト値",
	confidence: "信頼度",
	support: "支持度",
};

/** GET /api/basket/pairs を呼び出して併売ペアランキングを取得する。 */
export function fetchBasketPairs(query: BasketQuery): Promise<BasketPairsResponse> {
	return fetchJson<BasketPairsResponse>("/api/basket/pairs", {
		segment: query.segment,
		sort: query.sort,
		limit: String(query.limit),
	});
}

export interface DirectedRelation {
	fromName: string;
	toName: string;
	confidence: number;
}

/** 信頼度が高い方向を主方向として返す（例: 鮭おにぎり→緑茶）。同値なら A→B を採用する。 */
export function directedRelation(pair: BasketPair): DirectedRelation {
	if (pair.confidenceAToB >= pair.confidenceBToA) {
		return {
			fromName: pair.productNameA,
			toName: pair.productNameB,
			confidence: pair.confidenceAToB,
		};
	}
	return {
		fromName: pair.productNameB,
		toName: pair.productNameA,
		confidence: pair.confidenceBToA,
	};
}

/** 「鮭おにぎり → 緑茶500ml 65%」形式に文章化する。 */
export function directedLabel(pair: BasketPair): string {
	const relation = directedRelation(pair);
	return `${relation.fromName} → ${relation.toName} ${formatPercent(relation.confidence)}`;
}

/** 0..1 の比率を百分率の整数文字列にする。 */
export function formatPercent(ratio: number): string {
	return `${Math.round(ratio * 100)}%`;
}

export const SEGMENTS: readonly BasketSegment[] = ["all", "morning", "noon", "evening"];
export const SORTS: readonly BasketSortKey[] = ["lift", "confidence", "support"];

/** 文字列が時間帯セグメントの enum 値か判定する型ガード。 */
export function isBasketSegment(value: string): value is BasketSegment {
	return SEGMENTS.some((segment) => segment === value);
}

/** 文字列がソート軸の enum 値か判定する型ガード。 */
export function isBasketSortKey(value: string): value is BasketSortKey {
	return SORTS.some((key) => key === value);
}
