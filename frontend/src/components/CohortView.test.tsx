import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { CohortMatrixResponse, RfmResponse } from "../lib/cohort";
import { CohortView } from "./CohortView";

const cohortSample: CohortMatrixResponse = {
	hasMemberData: true,
	from: "2026-01",
	to: "2026-03",
	maxMonthOffset: 2,
	cohorts: [
		{
			cohortMonth: "2026-01",
			cohortSize: 3,
			cells: [
				{ monthOffset: 0, activeCustomers: 3, retentionRate: 1.0, totalSales: 540 },
				{ monthOffset: 1, activeCustomers: 2, retentionRate: 0.667, totalSales: 360 },
				{ monthOffset: 2, activeCustomers: 1, retentionRate: 0.333, totalSales: 180 },
			],
		},
		{
			cohortMonth: "2026-02",
			cohortSize: 1,
			cells: [{ monthOffset: 0, activeCustomers: 1, retentionRate: 1.0, totalSales: 180 }],
		},
	],
};

const rfmSample: RfmResponse = {
	hasMemberData: true,
	asOf: "2026-06-12",
	totalCustomers: 9,
	segments: [
		{
			segment: "loyal",
			label: "優良",
			description: "最近も来店があり、頻度・金額も高い中核顧客",
			customerCount: 4,
			avgOrderValue: 406.45,
			avgFrequency: 31,
			avgMonetary: 12600,
			avgRecencyDays: 1.5,
			lastVisit: "2026-06-12",
			estimatedLtv: 9754.8,
		},
		{
			segment: "at_risk",
			label: "離脱リスク",
			description: "以前は高頻度・高金額だが最近の来店が途絶えた顧客",
			customerCount: 2,
			avgOrderValue: 406.45,
			avgFrequency: 31,
			avgMonetary: 12600,
			avgRecencyDays: 150,
			lastVisit: "2026-01-12",
			estimatedLtv: 3251.6,
		},
		{
			segment: "dormant",
			label: "休眠",
			description: "来店頻度・金額が低い低活性顧客",
			customerCount: 3,
			avgOrderValue: 550,
			avgFrequency: 2,
			avgMonetary: 1100,
			avgRecencyDays: 90,
			lastVisit: "2026-06-11",
			estimatedLtv: 2200,
		},
	],
};

describe("CohortView", () => {
	it("初期(ローディング)状態でも見出しと期間セレクタを描画する", () => {
		const html = renderToString(<CohortView />);
		expect(html).toContain("顧客コホート / RFM 分析");
		expect(html).toContain("開始月");
		expect(html).toContain("読み込み中");
	});

	it("initialData を与えるとコホートマトリクスと RFM カードを描画する", () => {
		const html = renderToString(<CohortView initialCohort={cohortSample} initialRfm={rfmSample} />);
		// React SSR は補間値の前後に <!-- --> を挿入するため、テキスト照合前に除去する。
		const text = html.replace(/<!-- -->/g, "");
		// D3 マトリクス（色に加えて継続率の数値を併記）
		expect(html).toContain("<svg");
		expect(text).toContain("100%");
		expect(text).toContain("67%");
		expect(text).toContain("33%");
		// RFM 3セグメントのカード
		expect(text).toContain("優良");
		expect(text).toContain("離脱リスク");
		expect(text).toContain("休眠");
		// 件数・平均客単価・基準日
		expect(text).toContain("会員 9人");
		expect(text).toContain("¥550");
		expect(text).toContain("2026-06-12");
	});

	it("会員データ無しの initialData ではフォールバック案内を表示する", () => {
		const emptyCohort: CohortMatrixResponse = {
			hasMemberData: false,
			from: "2026-01",
			to: "2026-06",
			maxMonthOffset: 0,
			cohorts: [],
		};
		const emptyRfm: RfmResponse = { hasMemberData: false, asOf: null, totalCustomers: 0, segments: [] };
		const html = renderToString(<CohortView initialCohort={emptyCohort} initialRfm={emptyRfm} />);
		expect(html).toContain("コホート分析には会員データが必要です");
		expect(html).toContain("RFM 分析には会員データが必要です");
		expect(html).not.toContain("<svg");
	});
});
