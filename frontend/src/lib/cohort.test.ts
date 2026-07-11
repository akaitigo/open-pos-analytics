import { describe, expect, it } from "vitest";
import type { CohortMatrixResponse, RfmResponse } from "./cohort";
import {
	buildCohortGrid,
	cohortFallbackMessage,
	defaultRange,
	describeCohortCell,
	formatPercent,
	formatYen,
	offsetLabel,
	retentionTextColor,
	rfmFallbackMessage,
	segmentAccent,
} from "./cohort";

const matrix: CohortMatrixResponse = {
	hasMemberData: true,
	from: "2026-01",
	to: "2026-03",
	maxMonthOffset: 2,
	cohorts: [
		{
			cohortMonth: "2026-01",
			cohortSize: 3,
			cells: [
				{ monthOffset: 0, activeCustomers: 3, retentionRate: 1.0, totalSales: 540 },
				{ monthOffset: 1, activeCustomers: 2, retentionRate: 0.667, totalSales: 360 },
				{ monthOffset: 2, activeCustomers: 1, retentionRate: 0.333, totalSales: 180 },
			],
		},
		{
			cohortMonth: "2026-02",
			cohortSize: 1,
			cells: [{ monthOffset: 0, activeCustomers: 1, retentionRate: 1.0, totalSales: 180 }],
		},
	],
};

describe("buildCohortGrid", () => {
	it("offset 0..maxMonthOffset の密グリッドへ展開する", () => {
		const grid = buildCohortGrid(matrix);
		expect(grid.columns).toEqual([0, 1, 2]);
		expect(grid.rows).toHaveLength(2);
		expect(grid.rows[0]?.cells).toHaveLength(3);
		expect(grid.rows[0]?.cells[1]?.cell?.retentionRate).toBe(0.667);
	});

	it("データの無いセルは null（三角形状）", () => {
		const grid = buildCohortGrid(matrix);
		// 2026-02 コホートは offset0 のみ持ち、offset1/2 は null
		expect(grid.rows[1]?.cells[0]?.cell?.activeCustomers).toBe(1);
		expect(grid.rows[1]?.cells[1]?.cell).toBeNull();
		expect(grid.rows[1]?.cells[2]?.cell).toBeNull();
	});
});

describe("整形関数", () => {
	it("formatPercent は継続率をパーセント整数にする", () => {
		expect(formatPercent(1.0)).toBe("100%");
		expect(formatPercent(0.667)).toBe("67%");
		expect(formatPercent(0.333)).toBe("33%");
	});

	it("formatYen は円区切りにする", () => {
		expect(formatYen(550)).toBe("¥550");
		expect(formatYen(12800)).toBe("¥12,800");
	});

	it("offsetLabel は 0 を初月にする", () => {
		expect(offsetLabel(0)).toBe("初月");
		expect(offsetLabel(3)).toBe("3ヶ月後");
	});

	it("retentionTextColor は濃色セルで白文字", () => {
		expect(retentionTextColor(0.9)).toBe("#fff");
		expect(retentionTextColor(0.2)).toBe("#333");
	});

	it("segmentAccent はセグメント別の色、未知は既定色", () => {
		expect(segmentAccent("loyal")).toBe("#2e7d32");
		expect(segmentAccent("at_risk")).toBe("#c62828");
		expect(segmentAccent("dormant")).toBe("#616161");
		expect(segmentAccent("unknown")).toBe("#37474f");
	});

	it("describeCohortCell は色に依存しない説明文", () => {
		const text = describeCohortCell("2026-01", {
			monthOffset: 1,
			activeCustomers: 2,
			retentionRate: 0.667,
			totalSales: 360,
		});
		expect(text).toContain("2026-01");
		expect(text).toContain("1ヶ月後");
		expect(text).toContain("67%");
		expect(text).toContain("2人");
	});
});

describe("cohortFallbackMessage", () => {
	it("会員データ無しは案内文（デグレード動作）", () => {
		const response: CohortMatrixResponse = { ...matrix, hasMemberData: false, cohorts: [] };
		expect(cohortFallbackMessage(response)).toContain("会員データが必要");
	});

	it("会員はいるが該当期間にコホート無しは期間変更の案内", () => {
		const response: CohortMatrixResponse = { ...matrix, hasMemberData: true, cohorts: [] };
		expect(cohortFallbackMessage(response)).toContain("期間を広げて");
	});

	it("表示可能なら null", () => {
		expect(cohortFallbackMessage(matrix)).toBeNull();
	});
});

describe("rfmFallbackMessage", () => {
	const rfm: RfmResponse = { hasMemberData: true, asOf: "2026-06-12", totalCustomers: 9, segments: [] };

	it("会員データ無しは案内文", () => {
		expect(rfmFallbackMessage({ ...rfm, hasMemberData: false })).toContain("会員データが必要");
	});

	it("会員データありは null", () => {
		expect(rfmFallbackMessage(rfm)).toBeNull();
	});
});

describe("defaultRange", () => {
	it("当月を終端とする直近12ヶ月の窓を返す", () => {
		const range = defaultRange(new Date(2026, 6, 15));
		expect(range.to).toBe("2026-07");
		expect(range.from).toBe("2025-08");
	});
});
