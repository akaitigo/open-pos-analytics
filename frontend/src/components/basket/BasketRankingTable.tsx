import type { BasketPair } from "../../lib/basket";
import { directedLabel, directedRelation, formatPercent } from "../../lib/basket";

interface Props {
	pairs: BasketPair[];
}

/** 併売ペアを「A → B XX%」の文章化列を含むランキング表として描画する。 */
export function BasketRankingTable({ pairs }: Props) {
	if (pairs.length === 0) {
		return <p>該当する併売ペアがありません。</p>;
	}
	return (
		<table>
			<thead>
				<tr>
					<th>#</th>
					<th>併売関係</th>
					<th>共起回数</th>
					<th>支持度</th>
					<th>信頼度</th>
					<th>リフト値</th>
				</tr>
			</thead>
			<tbody>
				{pairs.map((pair, index) => (
					<tr key={`${pair.productA}-${pair.productB}`}>
						<td>{index + 1}</td>
						<td>{directedLabel(pair)}</td>
						<td>{pair.pairCount}</td>
						<td>{formatPercent(pair.support)}</td>
						<td>{formatPercent(directedRelation(pair).confidence)}</td>
						<td>{pair.lift.toFixed(2)}</td>
					</tr>
				))}
			</tbody>
		</table>
	);
}
