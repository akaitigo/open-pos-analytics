import { useEffect, useMemo, useState } from "react";
import { fetchJson } from "../lib/api";
import type { CategoriesResponse, HeatmapResponse, Metric } from "../lib/heatmap";
import { ALL_CATEGORY, buildGrid, summarize, toMetric } from "../lib/heatmap";
import { HeatmapChart } from "./heatmap/HeatmapChart";
import { HeatmapSummary } from "./heatmap/HeatmapSummary";

interface HeatmapViewProps {
	// テスト/SSR で初期データを注入するための任意プロップ（本番は未指定でフェッチする）。
	initialData?: HeatmapResponse;
	initialCategories?: string[];
}

export function HeatmapView({ initialData, initialCategories }: HeatmapViewProps = {}) {
	const [categories, setCategories] = useState<string[]>(initialCategories ?? []);
	const [category, setCategory] = useState<string>(
		initialData && initialData.category !== ALL_CATEGORY ? initialData.category : "",
	);
	const [metric, setMetric] = useState<Metric>(toMetric(initialData?.metric ?? "sales"));
	const [data, setData] = useState<HeatmapResponse | null>(initialData ?? null);
	const [loading, setLoading] = useState<boolean>(!initialData);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		let cancelled = false;
		fetchJson<CategoriesResponse>("/api/categories")
			.then((res) => {
				if (!cancelled) {
					setCategories(res.categories);
				}
			})
			.catch((cause: unknown) => {
				if (!cancelled) {
					setError(errorMessage(cause));
				}
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		setLoading(true);
		const params: Record<string, string> = { metric };
		if (category) {
			params.category = category;
		}
		fetchJson<HeatmapResponse>("/api/heatmap", params)
			.then((res) => {
				if (!cancelled) {
					setData(res);
					setError(null);
				}
			})
			.catch((cause: unknown) => {
				if (!cancelled) {
					setError(errorMessage(cause));
				}
			})
			.finally(() => {
				if (!cancelled) {
					setLoading(false);
				}
			});
		return () => {
			cancelled = true;
		};
	}, [category, metric]);

	const view = useMemo(() => {
		if (!data) {
			return null;
		}
		return {
			grid: buildGrid(data.cells, metric),
			summary: summarize(data.cells, metric),
			empty: data.cells.length === 0,
		};
	}, [data, metric]);

	return (
		<section aria-labelledby="heatmap-title" className="heatmap-view">
			<h2 id="heatmap-title">時間帯×曜日の売上ヒートマップ</h2>
			<p className="heatmap-hint">
				シフト計画・カテゴリ別ピーク把握のためのビュー。色に加えて各セルに数値を表示します。
			</p>
			<div className="heatmap-controls" style={{ display: "flex", flexWrap: "wrap", gap: "1rem" }}>
				<label>
					カテゴリ{" "}
					<select value={category} onChange={(event) => setCategory(event.target.value)}>
						<option value="">全カテゴリ</option>
						{categories.map((name) => (
							<option key={name} value={name}>
								{name}
							</option>
						))}
					</select>
				</label>
				<label>
					指標{" "}
					<select value={metric} onChange={(event) => setMetric(toMetric(event.target.value))}>
						<option value="sales">売上</option>
						<option value="count">取引数</option>
					</select>
				</label>
			</div>
			{loading ? <p className="heatmap-status">読み込み中…</p> : null}
			{error ? (
				<p className="heatmap-status" role="alert">
					読み込みに失敗しました: {error}
				</p>
			) : null}
			{view && !view.empty ? (
				<>
					<HeatmapChart grid={view.grid} metric={metric} />
					<HeatmapSummary busiest={view.summary.busiest} quietest={view.summary.quietest} metric={metric} />
				</>
			) : null}
			{view?.empty ? <p className="heatmap-status">データがありません。取込・集計を実行してください。</p> : null}
		</section>
	);
}

function errorMessage(cause: unknown): string {
	return cause instanceof Error ? cause.message : "不明なエラー";
}
