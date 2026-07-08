// heatmap モジュールのデータ→表示変換ロジック（純粋関数）。
// backend GET /api/heatmap / /api/categories のレスポンス整形とサマリー算出を担う。

export type Metric = "sales" | "count";

export interface HeatmapCell {
	dow: number;
	hour: number;
	sales: number;
	count: number;
	itemCount: number;
}

export interface HeatmapResponse {
	metric: string;
	category: string;
	cells: HeatmapCell[];
}

export interface CategoriesResponse {
	categories: string[];
}

export interface RankedCell {
	cell: HeatmapCell;
	value: number;
}

export interface HeatmapGrid {
	cells: (HeatmapCell | null)[][];
	max: number;
}

export const DOW_COUNT = 7;
export const HOUR_COUNT = 24;
export const ALL_CATEGORY = "__ALL__";

const DOW_LABELS = ["日", "月", "火", "水", "木", "金", "土"];

/** クエリ値を Metric に丸める（count 以外は sales）。 */
export function toMetric(value: string): Metric {
	return value === "count" ? "count" : "sales";
}

export function dowLabel(dow: number): string {
	return DOW_LABELS[dow] ?? "?";
}

export function hourLabel(hour: number): string {
	return `${hour}`;
}

export function metricValue(cell: HeatmapCell, metric: Metric): number {
	return metric === "sales" ? cell.sales : cell.count;
}

/** 疎なセル配列を 7×24 の密グリッドに展開し、指標の最大値を求める。 */
export function buildGrid(cells: HeatmapCell[], metric: Metric): HeatmapGrid {
	const grid: (HeatmapCell | null)[][] = Array.from({ length: DOW_COUNT }, () =>
		Array.from({ length: HOUR_COUNT }, (): HeatmapCell | null => null),
	);
	let max = 0;
	for (const cell of cells) {
		const row = grid[cell.dow];
		if (row && cell.hour >= 0 && cell.hour < HOUR_COUNT) {
			row[cell.hour] = cell;
		}
		const value = metricValue(cell, metric);
		if (value > max) {
			max = value;
		}
	}
	return { cells: grid, max };
}

/** 繁忙TOP（指標降順）・閑散TOP（指標昇順、0は除外）を算出する。 */
export function summarize(
	cells: HeatmapCell[],
	metric: Metric,
	size = 3,
): { busiest: RankedCell[]; quietest: RankedCell[] } {
	const ranked: RankedCell[] = cells
		.map((cell) => ({ cell, value: metricValue(cell, metric) }))
		.filter((entry) => entry.value > 0);
	const busiest = [...ranked].sort(compareDesc).slice(0, size);
	const quietest = [...ranked].sort(compareAsc).slice(0, size);
	return { busiest, quietest };
}

function compareDesc(a: RankedCell, b: RankedCell): number {
	return b.value - a.value || a.cell.dow - b.cell.dow || a.cell.hour - b.cell.hour;
}

function compareAsc(a: RankedCell, b: RankedCell): number {
	return a.value - b.value || a.cell.dow - b.cell.dow || a.cell.hour - b.cell.hour;
}

function round1(value: number): number {
	return Math.round(value * 10) / 10;
}

/** セル内に収まる短縮表記（例: 12800 -> "12.8k"）。 */
export function formatCompact(value: number): string {
	if (value >= 1_000_000) {
		return `${round1(value / 1_000_000)}M`;
	}
	if (value >= 1_000) {
		return `${round1(value / 1_000)}k`;
	}
	return `${Math.round(value)}`;
}

/** 指標に応じた完全表記（売上は円、件数は「件」）。 */
export function formatMetricValue(value: number, metric: Metric): string {
	if (metric === "sales") {
		return `¥${Math.round(value).toLocaleString("ja-JP")}`;
	}
	return `${Math.round(value).toLocaleString("ja-JP")}件`;
}

export function cellLabel(cell: HeatmapCell, metric: Metric): string {
	return metric === "sales" ? formatCompact(cell.sales) : `${cell.count}`;
}

/** ツールチップ/スクリーンリーダ向けのセル説明（曜日・時間帯・金額・件数）。 */
export function describeCell(cell: HeatmapCell): string {
	return `${dowLabel(cell.dow)}曜 ${cell.hour}時 ・ 売上${formatMetricValue(cell.sales, "sales")} ・ ${cell.count}件`;
}

/** サマリー1行のテキスト（例: "金曜 18時 — ¥12,340（42件）"）。 */
export function summaryLine(entry: RankedCell, metric: Metric): string {
	return `${dowLabel(entry.cell.dow)}曜 ${entry.cell.hour}時 — ${formatMetricValue(entry.value, metric)}（${entry.cell.count}件）`;
}
