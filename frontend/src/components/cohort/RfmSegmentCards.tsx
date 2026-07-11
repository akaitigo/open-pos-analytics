import type { RfmResponse, RfmSegmentSummary } from "../../lib/cohort";
import { formatYen, rfmFallbackMessage, segmentAccent } from "../../lib/cohort";

interface SegmentCardProps {
	segment: RfmSegmentSummary;
}

/** RFM セグメント1枚のカード（件数・平均客単価・最終来店・簡易LTV）。 */
function SegmentCard({ segment }: SegmentCardProps) {
	const accent = segmentAccent(segment.segment);
	return (
		<li className="rfm-card" style={{ borderTop: `4px solid ${accent}`, padding: "0.75rem 1rem", minWidth: "15rem" }}>
			<h4 style={{ color: accent, margin: "0 0 0.25rem" }}>{segment.label}</h4>
			<p className="rfm-card-desc" style={{ fontSize: "0.85rem", color: "#555", margin: "0 0 0.5rem" }}>
				{segment.description}
			</p>
			<dl className="rfm-card-metrics" style={{ margin: 0 }}>
				<div>
					<dt>件数</dt>
					<dd>{segment.customerCount}人</dd>
				</div>
				<div>
					<dt>平均客単価</dt>
					<dd>{formatYen(segment.avgOrderValue)}</dd>
				</div>
				<div>
					<dt>最終来店</dt>
					<dd>
						{segment.lastVisit ?? "-"}（平均 {Math.round(segment.avgRecencyDays)}日前）
					</dd>
				</div>
				<div>
					<dt>推定LTV（簡易）</dt>
					<dd>{formatYen(segment.estimatedLtv)}</dd>
				</div>
			</dl>
		</li>
	);
}

interface RfmSegmentCardsProps {
	data: RfmResponse;
}

/**
 * RFM セグメント（優良/離脱リスク/休眠）のカード一覧。
 * 会員データが無い場合はフォールバック文言を表示する。
 */
export function RfmSegmentCards({ data }: RfmSegmentCardsProps) {
	const fallback = rfmFallbackMessage(data);
	if (fallback !== null) {
		return (
			<p className="rfm-status" role="note">
				{fallback}
			</p>
		);
	}
	return (
		<section aria-label="RFM セグメント" className="rfm-cards">
			<h3>
				RFM セグメント（基準日 {data.asOf ?? "-"} ・ 会員 {data.totalCustomers}人）
			</h3>
			<ul
				className="rfm-card-list"
				style={{ display: "flex", flexWrap: "wrap", gap: "1rem", listStyle: "none", padding: 0 }}
			>
				{data.segments.map((segment) => (
					<SegmentCard key={segment.segment} segment={segment} />
				))}
			</ul>
		</section>
	);
}
