package tree;

import java.util.*;

public class TreeMain {

    private static final String CSV = "src/data/genealogy.csv";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading " + CSV + " ...");
        AcademicGraph graph    = AcademicGraph.loadFromCSV(CSV);
        GraphAnalyzer analyzer = new GraphAnalyzer(graph);

        // ── Network stats ─────────────────────────────────────────────────────
        sep("NETWORK STATISTICS");
        GraphAnalyzer.NetworkStats s = analyzer.computeStats();
        System.out.printf("Total nodes          : %d%n", s.totalNodes);
        System.out.printf("  Seeds (Penn)       : %d%n", s.seedCount);
        System.out.printf("  Ancestors          : %d%n", s.ancestorCount);
        System.out.printf("  Descendants        : %d%n", s.descendantCount);
        System.out.printf("Avg degree           : %.2f%n", s.avgDegree);
        System.out.printf("Max degree           : %d  (%s)%n", s.maxDegree,
                s.mostConnectedPerson != null ? s.mostConnectedPerson.name : "N/A");
        System.out.printf("Connected components : %d  (largest: %d)%n",
                s.componentCount, s.largestComponentSize);

        // ── Ancestor depth per seed ───────────────────────────────────────────
        sep("ANCESTOR DEPTH BY SEED");
        List<Person> seeds = graph.getSeeds();
        seeds.sort(Comparator.comparing(p -> p.name));

        // collect [mgpId, depth] sorted by depth desc
        List<int[]> depthRows = new ArrayList<>();
        for (Person seed : seeds)
            depthRows.add(new int[]{seed.mgpId, analyzer.maxAncestorDepth(seed.mgpId)});
        depthRows.sort((a, b) -> b[1] - a[1]);

        System.out.printf("%-42s  %s%n", "Name", "Ancestor depth");
        for (int[] row : depthRows) {
            Person p = graph.getPerson(row[0]);
            System.out.printf("%-42s  %d%n", trunc(p.name, 42), row[1]);
        }

        // ── Deepest ancestor chain ────────────────────────────────────────────
        sep("DEEPEST ANCESTOR CHAIN");
        int deepSeedId = depthRows.get(0)[0];
        Person deepSeed = graph.getPerson(deepSeedId);
        Map<Integer, Integer> ancDist = analyzer.ancestorDistances(deepSeedId);
        int oldestId = ancDist.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(deepSeedId);
        List<Person> chain = ancestorChain(graph, deepSeedId, oldestId);
        System.out.println("From: " + deepSeed.name);
        for (int i = 0; i < chain.size(); i++) {
            Person p = chain.get(i);
            String inst = p.institution.isEmpty() ? "?" : p.institution;
            System.out.printf("  %3d. %s  |  %s  |  %s%n",
                    i + 1, trunc(p.name, 32), inst.length() > 28 ? inst.substring(0, 28) : inst,
                    p.year.isEmpty() ? "?" : p.year);
        }

        // ── Degrees of separation between Penn faculty ────────────────────────
        sep("DEGREES OF SEPARATION (Penn Faculty Pairs)");
        // find the pair with maximum separation
        int maxDist = -1;
        Person farA = null, farB = null;
        for (int i = 0; i < seeds.size(); i++) {
            for (int j = i + 1; j < seeds.size(); j++) {
                int d = analyzer.degreesOfSeparation(seeds.get(i).mgpId, seeds.get(j).mgpId);
                if (d > maxDist) { maxDist = d; farA = seeds.get(i); farB = seeds.get(j); }
            }
        }
        if (farA != null) {
            System.out.printf("Most separated pair (%d hops):%n  %s%n  %s%n",
                    maxDist, farA.name, farB.name);
            System.out.println("Path:");
            printPath(analyzer.shortestPath(farA.mgpId, farB.mgpId));
        }

        // three more sample pairs
        System.out.println();
        for (int i = 0; i + 1 < Math.min(seeds.size(), 7); i += 3) {
            Person a = seeds.get(i), b = seeds.get(i + 1);
            List<Person> path = analyzer.shortestPath(a.mgpId, b.mgpId);
            String hopStr = path.isEmpty() ? "disconnected" : (path.size() - 1) + " hops";
            System.out.printf("%s  ←→  %s : %s%n", a.name, b.name, hopStr);
            if (!path.isEmpty()) printPath(path);
            System.out.println();
        }

        // ── Common ancestors ──────────────────────────────────────────────────
        sep("CLOSEST COMMON ANCESTORS (Penn Faculty Pairs)");
        int shown = 0;
        for (int i = 0; i < seeds.size() && shown < 4; i++) {
            for (int j = i + 1; j < seeds.size() && shown < 4; j++) {
                Person a = seeds.get(i), b = seeds.get(j);
                List<int[]> cas = analyzer.commonAncestors(a.mgpId, b.mgpId);
                // skip trivially self-common (distA==0 || distB==0 means one IS the other's ancestor)
                if (cas.isEmpty() || (cas.get(0)[1] == 0 && cas.get(0)[2] == 0)) continue;
                shown++;
                System.out.printf("%n  %s  &  %s%n", a.name, b.name);
                int minTotal = cas.get(0)[1] + cas.get(0)[2];
                for (int[] ca : cas) {
                    if (ca[1] + ca[2] > minTotal) break;
                    Person anc = graph.getPerson(ca[0]);
                    if (anc == null) continue;
                    System.out.printf("    → %s (%s)  [%d + %d hops]%n",
                            anc.name, anc.year, ca[1], ca[2]);
                }
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** BFS upward to reconstruct the path from startId to targetId via advisor edges. */
    private static List<Person> ancestorChain(AcademicGraph graph, int startId, int targetId) {
        Map<Integer, Integer> parent = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();
        parent.put(startId, -1);
        queue.add(startId);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == targetId) break;
            for (int adv : graph.getAdvisors(cur))
                if (!parent.containsKey(adv)) { parent.put(adv, cur); queue.add(adv); }
        }
        if (!parent.containsKey(targetId)) return List.of();
        List<Person> path = new ArrayList<>();
        for (int cur = targetId; cur != -1; cur = parent.get(cur))
            path.add(graph.getPerson(cur));
        Collections.reverse(path);
        return path;
    }

    private static void printPath(List<Person> path) {
        for (int i = 0; i < path.size(); i++) {
            Person p = path.get(i);
            System.out.printf("  %2d. %-35s [%s]%s%n",
                    i + 1, trunc(p.name, 35), p.nodeType,
                    i < path.size() - 1 ? " →" : "");
        }
    }

    private static void sep(String title) {
        System.out.println();
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println("  " + title);
        System.out.println("─────────────────────────────────────────────────────────────");
    }

    private static String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
