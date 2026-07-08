import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("App", () => {
	it("プロジェクト名を表示する", () => {
		const html = renderToString(<App />);
		expect(html).toContain("open-pos-analytics");
	});
});
