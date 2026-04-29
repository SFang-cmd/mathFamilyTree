"""
visualize.py
Reads src/data/genealogy.csv and writes tree_view.html — a self-contained
D3 force-directed visualizer. Open tree_view.html in any browser to explore.
"""
import csv, json, pathlib

CSV = pathlib.Path("src/data/genealogy.csv")

nodes, edges, seen_edges = [], [], set()
by_id = {}

with open(CSV, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        mid = row["mgp_id"].strip()
        if not mid:
            continue
        by_id[mid] = row
        nodes.append({
            "id":        mid,
            "name":      row["name"],
            "inst":      row["institution"],
            "year":      row["year"],
            "type":      row["node_type"],
        })
        for col in ("advisor1_id", "advisor2_id", "advisor3_id"):
            aid = row[col].strip()
            if aid and (aid, mid) not in seen_edges:
                seen_edges.add((aid, mid))
                edges.append({"source": aid, "target": mid})

data_js = f"const NODES = {json.dumps(nodes)};\nconst EDGES = {json.dumps(edges)};"

html = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Penn Math Genealogy Tree</title>
<style>
  body { margin: 0; background: #111; color: #eee; font-family: sans-serif; }
  #info { position: fixed; top: 10px; right: 10px; background: #222; padding: 12px 16px;
          border-radius: 8px; max-width: 280px; font-size: 13px; line-height: 1.5;
          display: none; }
  #info h3 { margin: 0 0 6px; font-size: 15px; }
  #legend { position: fixed; bottom: 14px; left: 14px; font-size: 12px; }
  .dot { display: inline-block; width: 10px; height: 10px;
         border-radius: 50%; margin-right: 5px; }
  svg { width: 100vw; height: 100vh; }
  .link { stroke: #555; stroke-opacity: 0.5; }
  .node { cursor: pointer; stroke: #111; stroke-width: 1px; }
  .label { font-size: 9px; fill: #ccc; pointer-events: none; }
</style>
</head>
<body>
<div id="info"></div>
<div id="legend">
  <div><span class="dot" style="background:#4e9af1"></span>Seed (Penn faculty)</div>
  <div><span class="dot" style="background:#57c17b"></span>Ancestor</div>
  <div><span class="dot" style="background:#f0a04b"></span>Descendant</div>
</div>
<svg id="svg"></svg>
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js"></script>
<script>
DATA_PLACEHOLDER

const COLOR = { seed: "#4e9af1", ancestor: "#57c17b", descendant: "#f0a04b" };
const RADIUS = { seed: 7, ancestor: 4, descendant: 4 };

// Index nodes
const nodeById = Object.fromEntries(NODES.map(n => [n.id, n]));
// Keep only edges where both ends exist
const links = EDGES.filter(e => nodeById[e.source] && nodeById[e.target]);

const svg = d3.select("#svg");
const g   = svg.append("g");

svg.call(d3.zoom().scaleExtent([0.05, 4])
  .on("zoom", e => g.attr("transform", e.transform)));

const sim = d3.forceSimulation(NODES)
  .force("link",   d3.forceLink(links).id(d => d.id).distance(30).strength(0.4))
  .force("charge", d3.forceManyBody().strength(-60))
  .force("center", d3.forceCenter(window.innerWidth / 2, window.innerHeight / 2))
  .force("collide", d3.forceCollide(6));

const link = g.append("g").selectAll("line")
  .data(links).join("line").attr("class", "link");

const node = g.append("g").selectAll("circle")
  .data(NODES).join("circle")
  .attr("class", "node")
  .attr("r", d => RADIUS[d.type] || 4)
  .attr("fill", d => COLOR[d.type] || "#aaa")
  .call(d3.drag()
    .on("start", (e, d) => { if (!e.active) sim.alphaTarget(0.3).restart(); d.fx=d.x; d.fy=d.y; })
    .on("drag",  (e, d) => { d.fx = e.x; d.fy = e.y; })
    .on("end",   (e, d) => { if (!e.active) sim.alphaTarget(0); d.fx=null; d.fy=null; }))
  .on("click", (e, d) => {
    const box = document.getElementById("info");
    box.style.display = "block";
    box.innerHTML = `<h3>${d.name}</h3>
      <b>Type:</b> ${d.type}<br>
      <b>Institution:</b> ${d.inst || "—"}<br>
      <b>Year:</b> ${d.year || "—"}`;
  });

// Labels only for seed nodes (too many to label all)
g.append("g").selectAll("text")
  .data(NODES.filter(d => d.type === "seed")).join("text")
  .attr("class", "label")
  .text(d => d.name.split(" ").pop());  // last name only

sim.on("tick", () => {
  link.attr("x1", d => d.source.x).attr("y1", d => d.source.y)
      .attr("x2", d => d.target.x).attr("y2", d => d.target.y);
  node.attr("cx", d => d.x).attr("cy", d => d.y);
  g.selectAll("text.label")
    .attr("x", d => d.x + 6).attr("y", d => d.y + 3);
});
</script>
</body>
</html>
""".replace("DATA_PLACEHOLDER", data_js)

out = pathlib.Path("tree_view.html")
out.write_text(html, encoding="utf-8")
print(f"Written {out} — {len(nodes)} nodes, {len(edges)} edges")
print("Open tree_view.html in your browser.")
