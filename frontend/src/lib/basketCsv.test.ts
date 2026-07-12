import { describe, expect, it } from "vitest";
import type { BasketPair } from "./basket";
import { escapeCsvCell, pairsToCsv } from "./basketCsv";

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

describe("pairsToCsv", () => {
	it("ヘッダー + データ行を CRLF で結合する", () => {
		const csv = pairsToCsv([pair]);
		const lines = csv.split("\r\n");
		expect(lines).toHaveLength(2);
		expect(lines[0]).toContain("商品A");
		expect(lines[1]).toContain("鮭おにぎり");
		expect(lines[1]).toContain("0.6500");
		expect(lines[1]).toContain("2.1000");
	});

	it("0件ならヘッダーのみ", () => {
		expect(pairsToCsv([]).split("\r\n")).toHaveLength(1);
	});
});

describe("escapeCsvCell", () => {
	it("カンマ・引用符を含むセルをクォートする", () => {
		expect(escapeCsvCell("おにぎり,鮭")).toBe('"おにぎり,鮭"');
		expect(escapeCsvCell('5"リンゴ')).toBe('"5""リンゴ"');
	});

	it("数式インジェクションを無害化する（=,+,-,@ 先頭）", () => {
		expect(escapeCsvCell("=SUM(A1)")).toBe("'=SUM(A1)");
		expect(escapeCsvCell("@cmd")).toBe("'@cmd");
		expect(escapeCsvCell("-1+2")).toBe("'-1+2");
	});

	it("通常セルはそのまま", () => {
		expect(escapeCsvCell("緑茶500ml")).toBe("緑茶500ml");
	});
});
