import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("App", () => {
	it("プロジェクト名と3モジュールのタブを表示する", () => {
		const html = renderToString(<App />);
		expect(html).toContain("open-pos-analytics");
		expect(html).toContain("ヒートマップ");
		expect(html).toContain("併売分析");
		expect(html).toContain("顧客コホート");
	});

	it("初期表示は heatmap モジュール", () => {
		const html = renderToString(<App />);
		expect(html).toContain("Issue #9");
	});
});
