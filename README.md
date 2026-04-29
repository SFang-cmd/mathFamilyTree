# Penn Math Academic Genealogy

A Java scraper that builds the academic advisor-student genealogy graph for Penn Math faculty using the [Mathematics Genealogy Project (MGP)](https://genealogy.math.ndsu.nodak.edu/).

## What It Does

Starting from all current Penn Math faculty (standing, associated, lecturers/postdocs, visiting), the scraper:

1. Scrapes faculty names from the Penn Math department website
2. Looks each person up in MGP using a multi-step search strategy (strips middle initials, falls back to last-name-only for uncommon names, strips diacritics)
3. Walks the graph **bidirectionally** via BFS:
   - **Upward** from each faculty member through their advisors, their advisors' advisors, etc. (ancestors only follow their own advisors)
   - **Downward** through each faculty member's students, their students' students, etc. (descendants only follow their own students)
4. Writes the result to a CSV with node type tagged as `seed`, `ancestor`, or `descendant`

The ancestor chains trace back centuries — Jonathan Block's line, for example, runs through Raoul Bott, all the way back to Euler, Leibniz, and Omar Khayyam (1068).

## Prerequisites

- Java 11+
- Maven (`/opt/homebrew/bin/mvn` on Apple Silicon Mac)
- Python 3 (only for the optional visualizer)

## Running the Scraper

```bash
mvn package -q
java -jar target/hw5-scraper.jar
```

**Expected runtime:** 20–40 minutes. The scraper sleeps 300ms between MGP requests to avoid rate limiting. Watch the console for progress — each node visited is printed with the current queue size.

**Output:**
- `src/data/penn_faculty.csv` — matched Penn faculty with their MGP IDs
- `src/data/genealogy.csv` — full genealogy graph (~2,000 nodes for the full Penn faculty run)

## Running the Visualizer (temporary)

```bash
python3 visualize.py
open tree_view.html     # or just double-click the file
```

This generates a self-contained `tree_view.html` (D3 force-directed graph, CDN-loaded). Blue = Penn seed, green = ancestor, orange = descendant. Click any node to see name, institution, and year. Delete `tree_view.html` and `visualize.py` when done.

## File Structure

```
src/
  scraper/
    PennFacultyScraper.java   — fetches Penn Math pages, finds MGP IDs
    MGPScraper.java           — bidirectional BFS over the MGP graph
    ScraperMain.java          — runs the full pipeline
    TestMain.java             — runs with a single hardcoded seed (for testing)
  tree/                       — Java tree implementation (in progress)
  data/
    penn_faculty.csv          — Penn faculty + MGP IDs
    genealogy.csv             — full genealogy graph
pom.xml
visualize.py                  — generates tree_view.html (delete after use)
```

## CSV Schema — genealogy.csv

| Column | Description |
|--------|-------------|
| `mgp_id` | MGP integer ID |
| `name` | Full name |
| `institution` | PhD-granting institution |
| `year` | PhD year |
| `dissertation` | Dissertation title |
| `advisor1_id/name` | First advisor (by order of appearance on MGP page) |
| `advisor2_id/name` | Second advisor |
| `advisor3_id/name` | Third advisor (rare — e.g. medieval scholars with multiple mentors) |
| `node_type` | `seed` (Penn faculty), `ancestor`, or `descendant` |

Advisor assignment is order-based (not label-based) to correctly handle mathematicians with multiple degrees, each listing a separate "Advisor 1".

## NETS 1500 Concepts

- **Graph and Graph Algorithms:** The genealogy is a directed acyclic graph (DAG). The scraper implements bidirectional BFS with direction-aware traversal. Advisor parsing handles multi-degree and multi-advisor edge cases.
- **Social Networks:** The advisor-student relationship is a real-world mentorship network. Penn faculty form the seed set; ancestor chains reveal academic lineages; descendant subtrees show intellectual influence.
