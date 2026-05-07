package tree;

import java.util.*;

/**
 * Graph algorithms on the academic genealogy DAG.
 *
 * Edges are directed advisor → student.  "Undirected" methods treat the graph
 * as undirected for shortest-path / degrees-of-separation queries.
 */
public class GraphAnalyzer {

    private final AcademicGraph graph;

    public GraphAnalyzer(AcademicGraph graph) {
        this.graph = graph;
    }

    // ── BFS shortest path (undirected) ────────────────────────────────────────

    /**
     * Returns the shortest undirected path between two nodes, or an empty list
     * if they are in different connected components.
     */
    public List<Person> shortestPath(int srcId, int dstId) {
        if (!graph.hasNode(srcId) || !graph.hasNode(dstId)) return List.of();
        if (srcId == dstId) return List.of(graph.getPerson(srcId));

        Map<Integer, Integer> parent = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();
        parent.put(srcId, -1);
        queue.add(srcId);

        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == dstId) break;
            for (int nbr : graph.getNeighbors(cur)) {
                if (!parent.containsKey(nbr)) {
                    parent.put(nbr, cur);
                    queue.add(nbr);
                }
            }
        }

        if (!parent.containsKey(dstId)) return List.of();

        List<Person> path = new ArrayList<>();
        for (int cur = dstId; cur != -1; cur = parent.get(cur))
            path.add(graph.getPerson(cur));
        Collections.reverse(path);
        return path;
    }

    /** Degrees of separation between two nodes (-1 if disconnected). */
    public int degreesOfSeparation(int id1, int id2) {
        List<Person> path = shortestPath(id1, id2);
        return path.isEmpty() ? -1 : path.size() - 1;
    }

    // ── Ancestor traversal ────────────────────────────────────────────────────

    /**
     * BFS upward through advisor edges.
     * Returns a map of reachable ancestor ID → distance from startId.
     * The start node itself is included at distance 0.
     */
    public Map<Integer, Integer> ancestorDistances(int startId) {
        Map<Integer, Integer> dist = new HashMap<>();
        Queue<Integer> queue = new ArrayDeque<>();
        dist.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            for (int adv : graph.getAdvisors(cur)) {
                if (!dist.containsKey(adv)) {
                    dist.put(adv, dist.get(cur) + 1);
                    queue.add(adv);
                }
            }
        }
        return dist;
    }

    /** Maximum depth going upward from startId (longest ancestor chain). */
    public int maxAncestorDepth(int startId) {
        return ancestorDistances(startId).values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
    }

    // ── Common ancestors ──────────────────────────────────────────────────────

    /**
     * Finds shared ancestors of two nodes, sorted by total combined distance
     * (closest first).  Each entry holds [ancestorId, distFromA, distFromB].
     */
    public List<int[]> commonAncestors(int idA, int idB) {
        Map<Integer, Integer> distA = ancestorDistances(idA);
        Map<Integer, Integer> distB = ancestorDistances(idB);

        List<int[]> shared = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : distA.entrySet()) {
            Integer dB = distB.get(e.getKey());
            if (dB != null) shared.add(new int[]{e.getKey(), e.getValue(), dB});
        }
        shared.sort(Comparator.comparingInt(a -> a[1] + a[2]));
        return shared;
    }

    // ── Network statistics ────────────────────────────────────────────────────

    public NetworkStats computeStats() {
        int total = 0, seeds = 0, ancestors = 0, descendants = 0;
        long totalDegree = 0;
        int maxDegree = 0;
        Person mostConnected = null;

        for (Person p : graph.allPersons()) {
            total++;
            switch (p.nodeType) {
                case "seed":       seeds++;       break;
                case "ancestor":   ancestors++;   break;
                case "descendant": descendants++; break;
            }
            int deg = graph.getAdvisors(p.mgpId).size() + graph.getStudents(p.mgpId).size();
            totalDegree += deg;
            if (deg > maxDegree) { maxDegree = deg; mostConnected = p; }
        }

        // weakly connected components
        Set<Integer> unvisited = new HashSet<>();
        for (Person p : graph.allPersons()) unvisited.add(p.mgpId);
        int components = 0, largestComp = 0;
        while (!unvisited.isEmpty()) {
            components++;
            int compSize = 0;
            Queue<Integer> q = new ArrayDeque<>();
            int start = unvisited.iterator().next();
            unvisited.remove(start);
            q.add(start);
            while (!q.isEmpty()) {
                int cur = q.poll();
                compSize++;
                for (int nbr : graph.getNeighbors(cur))
                    if (unvisited.remove(nbr)) q.add(nbr);
            }
            if (compSize > largestComp) largestComp = compSize;
        }

        NetworkStats s = new NetworkStats();
        s.totalNodes         = total;
        s.seedCount          = seeds;
        s.ancestorCount      = ancestors;
        s.descendantCount    = descendants;
        s.avgDegree          = total == 0 ? 0 : (double) totalDegree / total;
        s.maxDegree          = maxDegree;
        s.mostConnectedPerson = mostConnected;
        s.componentCount     = components;
        s.largestComponentSize = largestComp;
        return s;
    }

    public static class NetworkStats {
        public int    totalNodes, seedCount, ancestorCount, descendantCount;
        public double avgDegree;
        public int    maxDegree;
        public Person mostConnectedPerson;
        public int    componentCount, largestComponentSize;
    }
}
