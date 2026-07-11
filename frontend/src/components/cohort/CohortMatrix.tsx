import { interpolateBlues, scaleSequential } from "d3";
import type { CohortCell, CohortMatrixResponse } from "../../lib/cohort";
import {
	buildCohortGrid,
	cohortFallbackMessage,
	describeCohortCell,
	formatPercent,
	offsetLabel,
	retentionTextColor,
} from "../../lib/cohort";

const CELL_W = 72;
const CELL_H = 40;
const MARGIN = { top: 32, right: 12, bottom: 8, left: 120 };
const EMPTY_FILL = "#f5f5f5";

type ColorScale = (value: number) => string;

interface CohortCellRectProps {
	rowIndex: number;
	colIndex: number;
	offset: number;
	cohortMonth: string;
	cell: CohortCell | null;
	color: ColorScale;
}

/** 1セル（背景色 + 継続率ラベル + <title>）。色に依存しないよう数値と説明を併記する。 */
function CohortCellRect({ rowIndex, colIndex, offset, cohortMonth, cell, color }: CohortCellRectProps) {
	const x = MARGIN.left + colIndex * CELL_W;
	const y = MARGIN.top + rowIndex * CELL_H;
	if (cell === null) {
		return (
			<rect x={x} y={y} width={CELL_W} height={CELL_H} fill={EMPTY_FILL} stroke="#fff" strokeWidth={1}>
				<title>{`${cohortMonth} 初回 ・ ${offsetLabel(offset)} ・ データなし`}</title>
			</rect>
		);
	}
	return (
		<g>
			<rect x={x} y={y} width={CELL_W} height={CELL_H} fill={color(cell.retentionRate)} stroke="#fff" strokeWidth={1}>
				<title>{describeCohortCell(cohortMonth, cell)}</title>
			</rect>
			<text
				x={x + CELL_W / 2}
				y={y + CELL_H / 2}
				textAnchor="middle"
				dominantBaseline="central"
				fontSize={11}
				fill={retentionTextColor(cell.retentionRate)}
			>
				{formatPercent(cell.retentionRate)}
			</text>
		</g>
	);
}

interface CohortMatrixProps {
	data: CohortMatrixResponse;
}

/**
 * 月次コホートのリピート率を三角マトリクスで描画する。
 * D3(scaleSequential + interpolateBlues)で色を決め、セルは JSX(<rect>/<text>)で描く。
 * DOM 操作を伴わないため renderToString でも描画でき、テスト可能。
 * 会員データが無い/期間に該当が無い場合はフォールバック文言を表示する。
 */
export function CohortMatrix({ data }: CohortMatrixProps) {
	const fallback = cohortFallbackMessage(data);
	if (fallback !== null) {
		return (
			<p className="cohort-status" role="note">
				{fallback}
			</p>
		);
	}
	const grid = buildCohortGrid(data);
	const width = MARGIN.left + CELL_W * grid.columns.length + MARGIN.right;
	const height = MARGIN.top + CELL_H * grid.rows.length + MARGIN.bottom;
	const color = scaleSequential(interpolateBlues).domain([0, 1]);

	return (
		<figure className="cohort-matrix" style={{ margin: 0 }}>
			<figcaption>月次コホート リピート率（初回購入月 × 経過月）</figcaption>
			<svg
				role="img"
				aria-label="月次コホートのリピート率マトリクス"
				viewBox={`0 0 ${width} ${height}`}
				style={{ width: "100%", maxWidth: width, height: "auto" }}
			>
				{grid.columns.map((offset, colIndex) => (
					<text
						key={`col-${offset}`}
						x={MARGIN.left + colIndex * CELL_W + CELL_W / 2}
						y={MARGIN.top - 12}
						textAnchor="middle"
						fontSize={11}
						fill="#444"
					>
						{offsetLabel(offset)}
					</text>
				))}
				{grid.rows.map((row, rowIndex) => (
					<text
						key={`row-${row.cohortMonth}`}
						x={MARGIN.left - 8}
						y={MARGIN.top + rowIndex * CELL_H + CELL_H / 2}
						textAnchor="end"
						dominantBaseline="central"
						fontSize={11}
						fill="#444"
					>
						{`${row.cohortMonth}（${row.cohortSize}人）`}
					</text>
				))}
				{grid.rows.map((row, rowIndex) =>
					row.cells.map((gridCell, colIndex) => (
						<CohortCellRect
							key={`c-${row.cohortMonth}-${gridCell.offset}`}
							rowIndex={rowIndex}
							colIndex={colIndex}
							offset={gridCell.offset}
							cohortMonth={row.cohortMonth}
							cell={gridCell.cell}
							color={color}
						/>
					)),
				)}
			</svg>
		</figure>
	);
}
