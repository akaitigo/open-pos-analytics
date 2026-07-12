import type { SimulationLinkDatum, SimulationNodeDatum } from "d3";
import type { BasketPair } from "./basket";

export interface GraphNode extends SimulationNodeDatum {
	id: string;
	label: string;
}

export interface GraphLink extends SimulationLinkDatum<GraphNode> {
	source: string | GraphNode;
	target: string | GraphNode;
	lift: number;
}

export interface BasketGraph {
	nodes: GraphNode[];
	links: GraphLink[];
}

/** 併売ペア配列を、商品ノード + 併売リンクのグラフ構造に変換する。 */
export function buildGraph(pairs: BasketPair[]): BasketGraph {
	const nodeMap = new Map<string, GraphNode>();
	const links: GraphLink[] = [];
	for (const pair of pairs) {
		if (!nodeMap.has(pair.productA)) {
			nodeMap.set(pair.productA, { id: pair.productA, label: pair.productNameA });
		}
		if (!nodeMap.has(pair.productB)) {
			nodeMap.set(pair.productB, { id: pair.productB, label: pair.productNameB });
		}
		links.push({ source: pair.productA, target: pair.productB, lift: pair.lift });
	}
	return { nodes: Array.from(nodeMap.values()), links };
}
