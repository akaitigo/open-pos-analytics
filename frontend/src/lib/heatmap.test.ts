import { describe, expect, it } from "vitest";
import type { HeatmapCell } from "./heatmap";
import {
	buildGrid,
	cellLabel,
	describeCell,
	dowLabel,
	formatCompact,
	formatMetricValue,
	metricValue,
	summarize,
	summaryLine,
	toMetric,
} from "./heatmap";

function cell(dow: number, hour: number, sales: number, count: number, itemCount = count): HeatmapCell {
	return { dow, hour, sales, count, itemCount };
}

describe("buildGrid", () => {
	it("疎なセルを 7×24 の密グリッドに配置し欠損は null", () => {
		const grid = buildGrid([cell(1, 12, 680, 2), cell(6, 18, 380, 1)], "sales");
		expect(grid.cells).toHaveLength(7);
		expect(grid.cells[0]).toHaveLength(24);
		expect(grid.cells[1]?.[12]?.sales).toBe(680);
		expect(grid.cells[6]?.[18]?.count).toBe(1);
		expect(grid.cells[0]?.[0]).toBeNull();
		expect(grid.max).toBe(680);
	});

	it("metric=count では max が取引数ベースになる", () => {
		const grid = buildGrid([cell(1, 12, 680, 2), cell(2, 9, 100, 5)], "count");
		expect(grid.max).toBe(5);
	});
});

describe("summarize", () => {
	const cells = [cell(1, 12, 680, 2), cell(6, 18, 380, 5), cell(2, 9, 100, 1), cell(3, 3, 50, 1)];

	it("繁忙TOP3は指標降順", () => {
		const { busiest } = summarize(cells, "sales", 3);
		expect(busiest.map((entry) => entry.cell.sales)).toEqual([680, 380, 100]);
	});

	it("閑散TOP3は指標昇順で0を除外", () => {
		const { quietest } = summarize(cells, "sales", 3);
		expect(quietest.map((entry) => entry.cell.sales)).toEqual([50, 100, 380]);
	});

	it("metric=count で順位が変わる", () => {
		const { busiest } = summarize(cells, "count", 1);
		expect(busiest[0]?.cell.count).toBe(5);
	});

	it("同値は曜日→時間で決定的に並ぶ", () => {
		const tied = [cell(3, 10, 100, 1), cell(1, 10, 100, 1), cell(1, 8, 100, 1)];
		const { busiest } = summarize(tied, "sales", 3);
		expect(busiest.map((entry) => [entry.cell.dow, entry.cell.hour])).toEqual([
			[1, 8],
			[1, 10],
			[3, 10],
		]);
	});
});

describe("フォーマッタ", () => {
	it("toMetric は count 以外を sales に丸める", () => {
		expect(toMetric("count")).toBe("count");
		expect(toMetric("sales")).toBe("sales");
		expect(toMetric("revenue")).toBe("sales");
	});

	it("formatCompact は k/M 単位に短縮する", () => {
		expect(formatCompact(680)).toBe("680");
		expect(formatCompact(12800)).toBe("12.8k");
		expect(formatCompact(2_400_000)).toBe("2.4M");
	});

	it("formatMetricValue は指標で単位が変わる", () => {
		expect(formatMetricValue(12340, "sales")).toBe("¥12,340");
		expect(formatMetricValue(42, "count")).toBe("42件");
	});

	it("metricValue は指標に応じた値を返す", () => {
		expect(metricValue(cell(1, 12, 680, 2), "sales")).toBe(680);
		expect(metricValue(cell(1, 12, 680, 2), "count")).toBe(2);
	});

	it("dowLabel は日本語曜日（0=日, 6=土）", () => {
		expect(dowLabel(0)).toBe("日");
		expect(dowLabel(6)).toBe("土");
	});

	it("cellLabel/describeCell/summaryLine の表記", () => {
		const c = cell(5, 18, 12340, 42, 77);
		expect(cellLabel(c, "sales")).toBe("12.3k");
		expect(cellLabel(c, "count")).toBe("42");
		expect(describeCell(c)).toContain("金曜 18時");
		expect(describeCell(c)).toContain("42件");
		expect(summaryLine({ cell: c, value: 12340 }, "sales")).toBe("金曜 18時 — ¥12,340（42件）");
	});
});
