// cohort モジュールのデータ→表示変換ロジック（純粋関数）。
// backend GET /api/cohort / /api/rfm のレスポンス整形・フォールバック判定・整形表示を担う。

export interface CohortCell {
	monthOffset: number;
	activeCustomers: number;
	retentionRate: number;
	totalSales: number;
}

export interface CohortRow {
	cohortMonth: string;
	cohortSize: number;
	cells: CohortCell[];
}

export interface CohortMatrixResponse {
	hasMemberData: boolean;
	from: string;
	to: string;
	maxMonthOffset: number;
	cohorts: CohortRow[];
}

export interface RfmSegmentSummary {
	segment: string;
	label: string;
	description: string;
	customerCount: number;
	avgOrderValue: number;
	avgFrequency: number;
	avgMonetary: number;
	avgRecencyDays: number;
	lastVisit: string | null;
	estimatedLtv: number;
}

export interface RfmResponse {
	hasMemberData: boolean;
	asOf: string | null;
	totalCustomers: number;
	segments: RfmSegmentSummary[];
}

export interface CohortGridCell {
	offset: number;
	cell: CohortCell | null;
}

export interface CohortGridRow {
	cohortMonth: string;
	cohortSize: number;
	cells: CohortGridCell[];
}

export interface CohortGrid {
	columns: number[];
	rows: CohortGridRow[];
}

// 継続率がこの値を超えるセルは濃色になるため白文字にしてコントラストを確保する。
export const LIGHT_TEXT_THRESHOLD = 0.55;
// 期間セレクタの既定の表示月数。
export const RANGE_MONTHS = 12;

const SEGMENT_ACCENT: Record<string, string> = {
	loyal: "#2e7d32",
	at_risk: "#c62828",
	dormant: "#616161",
};

/**
 * 疎なコホート行を offset 0..maxMonthOffset の密グリッドへ展開する。
 * 各コホートは初回月からの経過月ぶんしかセルを持たないため、欠損は null（三角形状になる）。
 */
export function buildCohortGrid(response: CohortMatrixResponse): CohortGrid {
	const columns = Array.from({ length: response.maxMonthOffset + 1 }, (_, offset) => offset);
	const rows = response.cohorts.map((row) => ({
		cohortMonth: row.cohortMonth,
		cohortSize: row.cohortSize,
		cells: columns.map((offset) => ({
			offset,
			cell: row.cells.find((entry) => entry.monthOffset === offset) ?? null,
		})),
	}));
	return { columns, rows };
}

/** 経過月の見出し（0 は「初月」）。 */
export function offsetLabel(offset: number): string {
	return offset === 0 ? "初月" : `${offset}ヶ月後`;
}

/** 継続率をパーセント整数で表示する（例: 0.667 -> "67%"）。 */
export function formatPercent(rate: number): string {
	return `${Math.round(rate * 100)}%`;
}

/** 金額を円表記にする（例: 12800 -> "¥12,800"）。 */
export function formatYen(value: number): string {
	return `¥${Math.round(value).toLocaleString("ja-JP")}`;
}

/** 継続率セルの文字色（濃色セルは白）。 */
export function retentionTextColor(rate: number): string {
	return rate > LIGHT_TEXT_THRESHOLD ? "#fff" : "#333";
}

/** セグメントのアクセントカラー（未知セグメントは既定色）。 */
export function segmentAccent(segment: string): string {
	return SEGMENT_ACCENT[segment] ?? "#37474f";
}

/** ツールチップ/スクリーンリーダ向けのセル説明（色に依存しない情報）。 */
export function describeCohortCell(cohortMonth: string, cell: CohortCell): string {
	return `${cohortMonth} 初回 ・ ${offsetLabel(cell.monthOffset)} ・ 継続率 ${formatPercent(cell.retentionRate)}（${cell.activeCustomers}人）`;
}

/**
 * コホート表示のフォールバック文言を返す。
 * - 会員データ無し: 案内文（技術リスク2 のデグレード動作）
 * - 会員はいるが該当期間にコホート無し: 期間変更の案内
 * - 表示可能: null
 */
export function cohortFallbackMessage(response: CohortMatrixResponse): string | null {
	if (!response.hasMemberData) {
		return "コホート分析には会員データが必要です。会員IDを含む取引データを取り込むと、初回購入月ごとのリピート率が表示されます。";
	}
	if (response.cohorts.length === 0) {
		return "指定した期間に該当するコホートがありません。期間を広げて再表示してください。";
	}
	return null;
}

/** RFM 表示のフォールバック文言（会員データ無しのみ）。 */
export function rfmFallbackMessage(response: RfmResponse): string | null {
	if (!response.hasMemberData) {
		return "RFM 分析には会員データが必要です。会員IDを含む取引データを取り込むと、優良・離脱リスク・休眠の各セグメントが表示されます。";
	}
	return null;
}

/** 既定の期間（当月を終端とする直近 RANGE_MONTHS ヶ月）。 */
export function defaultRange(reference: Date = new Date()): { from: string; to: string } {
	const fromDate = new Date(reference.getFullYear(), reference.getMonth() - (RANGE_MONTHS - 1), 1);
	return { from: monthString(fromDate), to: monthString(reference) };
}

function monthString(date: Date): string {
	const month = `${date.getMonth() + 1}`.padStart(2, "0");
	return `${date.getFullYear()}-${month}`;
}
