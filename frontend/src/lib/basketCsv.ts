import type { BasketPair } from "./basket";
import { directedRelation } from "./basket";

const HEADER: readonly string[] = [
	"商品A",
	"商品B",
	"主方向",
	"共起回数",
	"支持度",
	"信頼度_A_to_B",
	"信頼度_B_to_A",
	"リフト値",
];

/** 併売ペア配列を CSV 文字列に変換する（陳列改善の持ち出し用）。 */
export function pairsToCsv(pairs: BasketPair[]): string {
	const lines = [HEADER.map(escapeCsvCell).join(",")];
	for (const pair of pairs) {
		lines.push(rowFor(pair).map(escapeCsvCell).join(","));
	}
	return lines.join("\r\n");
}

function rowFor(pair: BasketPair): string[] {
	const relation = directedRelation(pair);
	return [
		pair.productNameA,
		pair.productNameB,
		`${relation.fromName} → ${relation.toName}`,
		String(pair.pairCount),
		pair.support.toFixed(4),
		pair.confidenceAToB.toFixed(4),
		pair.confidenceBToA.toFixed(4),
		pair.lift.toFixed(4),
	];
}

/** CSV セルのエスケープ + 数式インジェクション対策（先頭が =,+,-,@ なら ' を付与）。 */
export function escapeCsvCell(value: string): string {
	const guarded = /^[=+\-@]/.test(value) ? `'${value}` : value;
	if (needsQuoting(guarded)) {
		return `"${guarded.replace(/"/g, '""')}"`;
	}
	return guarded;
}

function needsQuoting(value: string): boolean {
	return value.includes('"') || value.includes(",") || value.includes("\n") || value.includes("\r");
}

/** ブラウザで CSV をダウンロードさせる（Excel 互換のため UTF-8 BOM を付与）。 */
export function downloadCsv(filename: string, csv: string): void {
	const bom = String.fromCodePoint(0xfeff);
	const blob = new Blob([`${bom}${csv}`], { type: "text/csv;charset=utf-8;" });
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	link.click();
	URL.revokeObjectURL(url);
}
