import { useEffect, useState } from "react";
import type { BasketPair, BasketSegment, BasketSortKey } from "../lib/basket";
import { fetchBasketPairs } from "../lib/basket";
import { downloadCsv, pairsToCsv } from "../lib/basketCsv";
import { BasketControls } from "./basket/BasketControls";
import { BasketNetworkGraph } from "./basket/BasketNetworkGraph";
import { BasketRankingTable } from "./basket/BasketRankingTable";

type LoadState = "loading" | "ready" | "error";
const PAIR_LIMIT = 20;

export function BasketView() {
	const [segment, setSegment] = useState<BasketSegment>("all");
	const [sort, setSort] = useState<BasketSortKey>("lift");
	const [pairs, setPairs] = useState<BasketPair[]>([]);
	const [state, setState] = useState<LoadState>("loading");

	useEffect(() => {
		let cancelled = false;
		setState("loading");
		fetchBasketPairs({ segment, sort, limit: PAIR_LIMIT })
			.then((response) => {
				if (!cancelled) {
					setPairs(response.pairs);
					setState("ready");
				}
			})
			.catch(() => {
				if (!cancelled) {
					setState("error");
				}
			});
		return () => {
			cancelled = true;
		};
	}, [segment, sort]);

	const handleExport = () => {
		downloadCsv(`basket-pairs-${segment}-${sort}.csv`, pairsToCsv(pairs));
	};

	return (
		<section>
			<h2>併売分析</h2>
			<BasketControls
				segment={segment}
				sort={sort}
				onSegmentChange={setSegment}
				onSortChange={setSort}
				onExport={handleExport}
				exportDisabled={pairs.length === 0}
			/>
			{state === "loading" && <p>読み込み中...</p>}
			{state === "error" && <p role="alert">併売データの取得に失敗しました。</p>}
			{state === "ready" && (
				<>
					<BasketRankingTable pairs={pairs} />
					<BasketNetworkGraph pairs={pairs} />
				</>
			)}
		</section>
	);
}
