import { useState } from "react";
import { BasketView } from "./components/BasketView";
import { CohortView } from "./components/CohortView";
import { HeatmapView } from "./components/HeatmapView";

const MODULES = [
	{ id: "heatmap", label: "ヒートマップ", component: HeatmapView },
	{ id: "basket", label: "併売分析", component: BasketView },
	{ id: "cohort", label: "顧客コホート", component: CohortView },
] as const;

type ModuleId = (typeof MODULES)[number]["id"];

export function App() {
	const [active, setActive] = useState<ModuleId>("heatmap");
	const ActiveComponent = MODULES.find((m) => m.id === active)?.component ?? HeatmapView;

	return (
		<main>
			<h1>open-pos-analytics</h1>
			<nav>
				{MODULES.map((m) => (
					<button key={m.id} type="button" aria-pressed={m.id === active} onClick={() => setActive(m.id)}>
						{m.label}
					</button>
				))}
			</nav>
			<ActiveComponent />
		</main>
	);
}
