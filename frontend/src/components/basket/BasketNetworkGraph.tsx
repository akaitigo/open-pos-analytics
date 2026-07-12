import * as d3 from "d3";
import { useEffect, useRef } from "react";
import type { BasketPair } from "../../lib/basket";
import type { GraphLink, GraphNode } from "../../lib/basketGraph";
import { buildGraph } from "../../lib/basketGraph";

interface Props {
	pairs: BasketPair[];
	width?: number;
	height?: number;
}

const DEFAULT_WIDTH = 640;
const DEFAULT_HEIGHT = 420;
const NODE_RADIUS = 8;
const MIN_STROKE = 1;
const MAX_STROKE = 8;
const LINK_DISTANCE = 120;
const CHARGE_STRENGTH = -260;

/** 上位ペアを D3 force-directed グラフ（商品ノード + 併売リンク）として描画する。 */
export function BasketNetworkGraph({ pairs, width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT }: Props) {
	const svgRef = useRef<SVGSVGElement>(null);

	useEffect(() => {
		const element = svgRef.current;
		if (element === null) {
			return;
		}
		const graph = buildGraph(pairs);
		const svg = d3.select(element);
		svg.selectAll("*").remove();
		if (graph.nodes.length === 0) {
			return;
		}

		const maxLift = Math.max(...graph.links.map((link) => link.lift), MIN_STROKE);
		const strokeScale = d3.scaleLinear().domain([MIN_STROKE, maxLift]).range([MIN_STROKE, MAX_STROKE]).clamp(true);

		const linkSelection = svg
			.append("g")
			.attr("stroke", "#9aa5b1")
			.attr("stroke-opacity", 0.6)
			.selectAll<SVGLineElement, GraphLink>("line")
			.data(graph.links)
			.join("line")
			.attr("stroke-width", (link) => strokeScale(link.lift));

		const nodeGroup = svg.append("g").selectAll<SVGGElement, GraphNode>("g").data(graph.nodes).join("g");

		nodeGroup.append("circle").attr("r", NODE_RADIUS).attr("fill", "#2f6fed");
		nodeGroup
			.append("text")
			.text((node) => node.label)
			.attr("x", NODE_RADIUS + 4)
			.attr("y", 4)
			.attr("font-size", "12px")
			.attr("fill", "#1f2933");

		const simulation = d3
			.forceSimulation<GraphNode>(graph.nodes)
			.force(
				"link",
				d3
					.forceLink<GraphNode, GraphLink>(graph.links)
					.id((node) => node.id)
					.distance(LINK_DISTANCE),
			)
			.force("charge", d3.forceManyBody().strength(CHARGE_STRENGTH))
			.force("center", d3.forceCenter(width / 2, height / 2))
			.on("tick", () => {
				linkSelection
					.attr("x1", (link) => endpointX(link.source))
					.attr("y1", (link) => endpointY(link.source))
					.attr("x2", (link) => endpointX(link.target))
					.attr("y2", (link) => endpointY(link.target));
				nodeGroup.attr("transform", (node) => `translate(${node.x ?? 0}, ${node.y ?? 0})`);
			});

		return () => {
			simulation.stop();
		};
	}, [pairs, width, height]);

	return <svg ref={svgRef} width={width} height={height} role="img" aria-label="併売ネットワーク図" />;
}

function endpointX(endpoint: string | number | GraphNode): number {
	return typeof endpoint === "object" ? (endpoint.x ?? 0) : 0;
}

function endpointY(endpoint: string | number | GraphNode): number {
	return typeof endpoint === "object" ? (endpoint.y ?? 0) : 0;
}
