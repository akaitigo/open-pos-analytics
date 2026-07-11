import { type FormEvent, useEffect, useMemo, useState } from "react";
import { fetchJson } from "../lib/api";
import type { CohortMatrixResponse, RfmResponse } from "../lib/cohort";
import { defaultRange } from "../lib/cohort";
import { CohortMatrix } from "./cohort/CohortMatrix";
import { RfmSegmentCards } from "./cohort/RfmSegmentCards";

interface CohortViewProps {
	// テスト/SSR で初期データを注入するための任意プロップ（本番は未指定でフェッチする）。
	initialCohort?: CohortMatrixResponse;
	initialRfm?: RfmResponse;
}

export function CohortView({ initialCohort, initialRfm }: CohortViewProps = {}) {
	const initialRange = useMemo(() => defaultRange(), []);
	const [from, setFrom] = useState<string>(initialCohort?.from ?? initialRange.from);
	const [to, setTo] = useState<string>(initialCohort?.to ?? initialRange.to);
	const [draftFrom, setDraftFrom] = useState<string>(from);
	const [draftTo, setDraftTo] = useState<string>(to);
	const [cohort, setCohort] = useState<CohortMatrixResponse | null>(initialCohort ?? null);
	const [rfm, setRfm] = useState<RfmResponse | null>(initialRfm ?? null);
	const [loading, setLoading] = useState<boolean>(!(initialCohort && initialRfm));
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		// initialData 注入時（テスト/SSR）は初回フェッチをスキップする。
		if (initialCohort && initialRfm) {
			return;
		}
		let cancelled = false;
		setLoading(true);
		Promise.all([fetchJson<CohortMatrixResponse>("/api/cohort", { from, to }), fetchJson<RfmResponse>("/api/rfm")])
			.then(([cohortData, rfmData]) => {
				if (!cancelled) {
					setCohort(cohortData);
					setRfm(rfmData);
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
	}, [from, to, initialCohort, initialRfm]);

	function applyRange(event: FormEvent<HTMLFormElement>) {
		event.preventDefault();
		setFrom(draftFrom);
		setTo(draftTo);
	}

	return (
		<section aria-labelledby="cohort-title" className="cohort-view">
			<h2 id="cohort-title">顧客コホート / RFM 分析</h2>
			<p className="cohort-hint">
				初回購入月ごとのリピート率と、優良・離脱リスク・休眠の RFM
				セグメントを表示します。色に加えて各セルに数値を併記します。
			</p>
			<form
				className="cohort-controls"
				onSubmit={applyRange}
				style={{ display: "flex", flexWrap: "wrap", gap: "1rem", alignItems: "end" }}
			>
				<label>
					開始月 <input type="month" value={draftFrom} onChange={(event) => setDraftFrom(event.target.value)} />
				</label>
				<label>
					終了月 <input type="month" value={draftTo} onChange={(event) => setDraftTo(event.target.value)} />
				</label>
				<button type="submit">表示</button>
			</form>
			{loading ? <p className="cohort-status">読み込み中…</p> : null}
			{error ? (
				<p className="cohort-status" role="alert">
					読み込みに失敗しました: {error}
				</p>
			) : null}
			{cohort ? <CohortMatrix data={cohort} /> : null}
			{rfm ? <RfmSegmentCards data={rfm} /> : null}
		</section>
	);
}

function errorMessage(cause: unknown): string {
	return cause instanceof Error ? cause.message : "不明なエラー";
}
