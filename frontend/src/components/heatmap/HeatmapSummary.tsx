import type { Metric, RankedCell } from "../../lib/heatmap";
import { summaryLine } from "../../lib/heatmap";

interface HeatmapSummaryProps {
	busiest: RankedCell[];
	quietest: RankedCell[];
	metric: Metric;
}

/** 店主が5分で読める繁忙/閑散サマリー（テキスト。色に依存しない要約）。 */
export function HeatmapSummary({ busiest, quietest, metric }: HeatmapSummaryProps) {
	return (
		<div className="heatmap-summary" style={{ display: "flex", flexWrap: "wrap", gap: "2rem" }}>
			<section aria-labelledby="heatmap-busy-title">
				<h3 id="heatmap-busy-title">繁忙 TOP3</h3>
				<ol>
					{busiest.map((entry) => (
						<li key={`busy-${entry.cell.dow}-${entry.cell.hour}`}>{summaryLine(entry, metric)}</li>
					))}
				</ol>
			</section>
			<section aria-labelledby="heatmap-quiet-title">
				<h3 id="heatmap-quiet-title">閑散 TOP3（営業実績のある時間帯）</h3>
				<ol>
					{quietest.map((entry) => (
						<li key={`quiet-${entry.cell.dow}-${entry.cell.hour}`}>{summaryLine(entry, metric)}</li>
					))}
				</ol>
			</section>
		</div>
	);
}
