import { interpolateYlOrRd, scaleSequential } from "d3";
import type { HeatmapCell, HeatmapGrid, Metric } from "../../lib/heatmap";
import { cellLabel, describeCell, dowLabel, hourLabel, metricValue } from "../../lib/heatmap";

const CELL_W = 40;
const CELL_H = 32;
const MARGIN = { top: 28, right: 12, bottom: 8, left: 44 };
const EMPTY_FILL = "#f2f2f2";
// 正規化値がこの閾値を超えたら白文字にしてコントラストを確保する。
const LIGHT_TEXT_THRESHOLD = 0.55;
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);
const DOWS = Array.from({ length: 7 }, (_, dow) => dow);

type ColorScale = (value: number) => string;

interface CellRectProps {
	dow: number;
	hour: number;
	cell: HeatmapCell | null;
	metric: Metric;
	max: number;
	color: ColorScale;
}

/** 1セル（背景色 + 値ラベル + <title>）。色に依存しないよう数値と説明を併記する。 */
function CellRect({ dow, hour, cell, metric, max, color }: CellRectProps) {
	const x = MARGIN.left + hour * CELL_W;
	const y = MARGIN.top + dow * CELL_H;
	const value = cell ? metricValue(cell, metric) : 0;
	const ratio = max > 0 ? value / max : 0;
	const fill = cell ? color(value) : EMPTY_FILL;
	const textFill = ratio > LIGHT_TEXT_THRESHOLD ? "#fff" : "#333";
	return (
		<g>
			<rect x={x} y={y} width={CELL_W} height={CELL_H} fill={fill} stroke="#fff" strokeWidth={1}>
				<title>{cell ? describeCell(cell) : `${dowLabel(dow)}曜 ${hour}時 ・ データなし`}</title>
			</rect>
			{cell ? (
				<text
					x={x + CELL_W / 2}
					y={y + CELL_H / 2}
					textAnchor="middle"
					dominantBaseline="central"
					fontSize={10}
					fill={textFill}
				>
					{cellLabel(cell, metric)}
				</text>
			) : null}
		</g>
	);
}

interface HeatmapChartProps {
	grid: HeatmapGrid;
	metric: Metric;
}

/**
 * D3(scaleSequential + interpolateYlOrRd)で色を決め、セルは JSX(<rect>)で描画する。
 * DOM 操作を伴わないため renderToString でもそのまま描画でき、テスト可能。
 */
export function HeatmapChart({ grid, metric }: HeatmapChartProps) {
	const width = MARGIN.left + CELL_W * 24 + MARGIN.right;
	const height = MARGIN.top + CELL_H * 7 + MARGIN.bottom;
	const color = scaleSequential(interpolateYlOrRd).domain([0, grid.max || 1]);
	const metricLabel = metric === "sales" ? "売上" : "取引数";

	return (
		<svg
			role="img"
			aria-label={`時間帯×曜日ヒートマップ（指標: ${metricLabel}）`}
			viewBox={`0 0 ${width} ${height}`}
			style={{ width: "100%", maxWidth: width, height: "auto" }}
		>
			{HOURS.map((hour) => (
				<text
					key={`h-${hour}`}
					x={MARGIN.left + hour * CELL_W + CELL_W / 2}
					y={MARGIN.top - 10}
					textAnchor="middle"
					fontSize={11}
					fill="#444"
				>
					{hourLabel(hour)}
				</text>
			))}
			{DOWS.map((dow) => (
				<text
					key={`d-${dow}`}
					x={MARGIN.left - 8}
					y={MARGIN.top + dow * CELL_H + CELL_H / 2}
					textAnchor="end"
					dominantBaseline="central"
					fontSize={12}
					fill="#444"
				>
					{dowLabel(dow)}
				</text>
			))}
			{DOWS.map((dow) =>
				HOURS.map((hour) => (
					<CellRect
						key={`c-${dow}-${hour}`}
						dow={dow}
						hour={hour}
						cell={grid.cells[dow]?.[hour] ?? null}
						metric={metric}
						max={grid.max}
						color={color}
					/>
				)),
			)}
		</svg>
	);
}
