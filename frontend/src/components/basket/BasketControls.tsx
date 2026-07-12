import type { ChangeEvent } from "react";
import type { BasketSegment, BasketSortKey } from "../../lib/basket";
import { SEGMENTS, SEGMENT_LABELS, SORTS, SORT_LABELS, isBasketSortKey } from "../../lib/basket";

interface Props {
	segment: BasketSegment;
	sort: BasketSortKey;
	onSegmentChange: (segment: BasketSegment) => void;
	onSortChange: (sort: BasketSortKey) => void;
	onExport: () => void;
	exportDisabled: boolean;
}

/** 時間帯セグメント切替（ボタン群）+ ソート軸切替（select）+ CSVエクスポートのコントロール。 */
export function BasketControls({ segment, sort, onSegmentChange, onSortChange, onExport, exportDisabled }: Props) {
	const handleSortChange = (event: ChangeEvent<HTMLSelectElement>) => {
		const value = event.target.value;
		if (isBasketSortKey(value)) {
			onSortChange(value);
		}
	};

	return (
		<div className="basket-controls">
			<fieldset>
				<legend>時間帯</legend>
				{SEGMENTS.map((value) => (
					<button key={value} type="button" aria-pressed={value === segment} onClick={() => onSegmentChange(value)}>
						{SEGMENT_LABELS[value]}
					</button>
				))}
			</fieldset>
			<label>
				並び替え
				<select value={sort} onChange={handleSortChange}>
					{SORTS.map((value) => (
						<option key={value} value={value}>
							{SORT_LABELS[value]}
						</option>
					))}
				</select>
			</label>
			<button type="button" onClick={onExport} disabled={exportDisabled}>
				CSVエクスポート
			</button>
		</div>
	);
}
