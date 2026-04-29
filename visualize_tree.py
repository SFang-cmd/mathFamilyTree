"""
visualize_tree.py
Reads src/data/genealogy.csv and writes tree_hierarchical.html — a top-down
Dagre-layout hierarchical tree. Open tree_hierarchical.html in any browser.
"""
import csv, json, pathlib

CSV = pathlib.Path("src/data/genealogy.csv")

nodes, edges, seen_edges, id_set = [], [], set(), set()

with open(CSV, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        mid = row["mgp_id"].strip()
        if not mid:
            continue
        id_set.add(mid)
        nodes.append({
            "id":   mid,
            "name": row["name"],
            "inst": row["institution"],
            "year": row["year"],
            "type": row["node_type"],
        })

with open(CSV, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        mid = row["mgp_id"].strip()
        for col in ("advisor1_id", "advisor2_id", "advisor3_id"):
            aid = row[col].strip()
            # only include edges where both nodes are in our dataset
            if aid and aid in id_set and (aid, mid) not in seen_edges:
                seen_edges.add((aid, mid))
                edges.append({"source": aid, "target": mid})

data_js = f"const NODES = {json.dumps(nodes)};\nconst EDGES = {json.dumps(edges)};"

html = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Penn Math Genealogy — Hierarchical Tree</title>
<style>
  body { margin: 0; background: #111; color: #eee; font-family: sans-serif; overflow: hidden; }
  #status { position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%);
            font-size: 18px; color: #aaa; }
  #info { position: fixed; top: 10px; right: 10px; background: #222; padding: 12px 16px;
          border-radius: 8px; max-width: 280px; font-size: 13px; line-height: 1.6; display: none;
          border: 1px solid #444; }
  #info h3 { margin: 0 0 6px; font-size: 15px; color: #fff; }
  #legend { position: fixed; bottom: 14px; left: 14px; font-size: 12px; line-height: 1.8; }
  .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
  svg { width: 100vw; height: 100vh; }
</style>
</head>
<body>
<div id="status">Computing hierarchical layout…</div>
<div id="info"></div>
<div id="legend">
  <div><span class="dot" style="background:#4e9af1"></span>Seed (Penn faculty)</div>
  <div><span class="dot" style="background:#57c17b"></span>Ancestor</div>
  <div><span class="dot" style="background:#f0a04b"></span>Descendant</div>
</div>
<svg id="svg"></svg>
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/dagre/0.8.5/dagre.min.js"></script>
<script>
DATA_PLACEHOLDER

const COLOR  = { seed: "#4e9af1", ancestor: "#57c17b", descendant: "#f0a04b" };
const RADIUS = { seed: 7, ancestor: 4, descendant: 4 };

const nodeById = Object.fromEntries(NODES.map(n => [n.id, n]));
const links    = EDGES.filter(e => nodeById[e.source] && nodeById[e.target]);

// Defer layout so the browser renders "Computing…" first
setTimeout(() => {
    const dg = new dagre.graphlib.Graph({ multigraph: false });
    dg.setGraph({ rankdir: "TB", nodesep: 6, ranksep: 28, marginx: 40, marginy: 40 });
    dg.setDefaultEdgeLabel(() => ({}));

    NODES.forEach(n => dg.setNode(n.id, { width: (RADIUS[n.type]||4)*2, height: (RADIUS[n.type]||4)*2 }));
    links.forEach(e => dg.setEdge(e.source, e.target));

    dagre.layout(dg);

    NODES.forEach(n => {
        const pos = dg.node(n.id);
        if (pos) { n.x = pos.x; n.y = pos.y; }
        else      { n.x = 0;    n.y = 0;     }
    });

    document.getElementById("status").style.display = "none";
    draw(dg.graph());
}, 30);

function draw(graphMeta) {
    const svg   = d3.select("#svg");
    const group = svg.append("g");
    const W = window.innerWidth, H = window.innerHeight;

    const zoom = d3.zoom()
        .scaleExtent([0.01, 6])
        .on("zoom", e => group.attr("transform", e.transform));
    svg.call(zoom);

    // Edges — cubic bezier top-to-bottom
    group.append("g").attr("fill", "none")
        .selectAll("path")
        .data(links).join("path")
        .attr("stroke", "#3a3a3a")
        .attr("stroke-width", 0.8)
        .attr("d", d => {
            const s = nodeById[d.source], t = nodeById[d.target];
            const my = (s.y + t.y) / 2;
            return `M${s.x},${s.y} C${s.x},${my} ${t.x},${my} ${t.x},${t.y}`;
        });

    // Nodes
    group.append("g")
        .selectAll("circle")
        .data(NODES).join("circle")
        .attr("r",  d => RADIUS[d.type] || 4)
        .attr("cx", d => d.x)
        .attr("cy", d => d.y)
        .attr("fill",         d => COLOR[d.type] || "#aaa")
        .attr("stroke",       "#111")
        .attr("stroke-width", 1)
        .style("cursor", "pointer")
        .on("click", (event, d) => {
            event.stopPropagation();
            const box = document.getElementById("info");
            box.style.display = "block";
            box.innerHTML = `<h3>${d.name}</h3>
                <b>Type:</b> ${d.type}<br>
                <b>Institution:</b> ${d.inst || "—"}<br>
                <b>Year:</b> ${d.year || "—"}`;
        });

    // Labels — seed nodes only (last name)
    group.append("g")
        .selectAll("text")
        .data(NODES.filter(d => d.type === "seed")).join("text")
        .attr("x", d => d.x + 9)
        .attr("y", d => d.y + 4)
        .attr("font-size", "9px")
        .attr("fill", "#ccc")
        .attr("pointer-events", "none")
        .text(d => d.name.split(" ").pop());

    // Dismiss info panel on background click
    svg.on("click", () => { document.getElementById("info").style.display = "none"; });

    // Fit entire graph into viewport on load
    const gw = graphMeta.width  || W;
    const gh = graphMeta.height || H;
    const scale = Math.min(W / gw, H / gh) * 0.9;
    svg.call(zoom.transform, d3.zoomIdentity
        .translate((W - gw * scale) / 2, (H - gh * scale) / 2)
        .scale(scale));
}
</script>
</body>
</html>
""".replace("DATA_PLACEHOLDER", data_js)

out = pathlib.Path("tree_hierarchical.html")
out.write_text(html, encoding="utf-8")
print(f"Written {out} — {len(nodes)} nodes, {len(edges)} edges")
print("Open tree_hierarchical.html in your browser.")
