package tree;

import java.io.*;
import java.util.*;

public class AcademicGraph {

    private final Map<Integer, Person>       nodes    = new LinkedHashMap<>();
    // directed edges: student → advisors, advisor → students
    private final Map<Integer, List<Integer>> advisors = new HashMap<>();
    private final Map<Integer, List<Integer>> students = new HashMap<>();

    // ── loading ──────────────────────────────────────────────────────────────

    public static AcademicGraph loadFromCSV(String path) throws IOException {
        AcademicGraph g = new AcademicGraph();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> f = parseLine(line);
                if (f.size() < 12) continue;
                try {
                    int id = Integer.parseInt(f.get(0).trim());
                    Person p = new Person(
                            id,
                            f.get(1).trim(), f.get(2).trim(), f.get(3).trim(), f.get(4).trim(),
                            parseId(f.get(5)),  f.get(6).trim(),
                            parseId(f.get(7)),  f.get(8).trim(),
                            parseId(f.get(9)),  f.get(10).trim(),
                            f.get(11).trim()
                    );
                    g.nodes.put(id, p);
                    g.advisors.computeIfAbsent(id, k -> new ArrayList<>());
                    g.students.computeIfAbsent(id, k -> new ArrayList<>());
                } catch (NumberFormatException ignored) {}
            }
        }

        // build directed edges (only between nodes that exist in the graph)
        for (Person p : g.nodes.values()) {
            for (int advId : new int[]{p.advisor1Id, p.advisor2Id, p.advisor3Id}) {
                if (advId > 0 && g.nodes.containsKey(advId)) {
                    g.advisors.get(p.mgpId).add(advId);
                    g.students.computeIfAbsent(advId, k -> new ArrayList<>()).add(p.mgpId);
                }
            }
        }

        return g;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Person             getPerson(int id)    { return nodes.get(id); }
    public boolean            hasNode(int id)      { return nodes.containsKey(id); }
    public Collection<Person> allPersons()         { return nodes.values(); }

    public List<Integer> getAdvisors(int id) {
        return advisors.getOrDefault(id, Collections.emptyList());
    }

    public List<Integer> getStudents(int id) {
        return students.getOrDefault(id, Collections.emptyList());
    }

    /** All neighbors in the undirected view (advisors + students). */
    public List<Integer> getNeighbors(int id) {
        List<Integer> out = new ArrayList<>(getAdvisors(id));
        out.addAll(getStudents(id));
        return out;
    }

    public List<Person> getSeeds() {
        List<Person> out = new ArrayList<>();
        for (Person p : nodes.values()) if ("seed".equals(p.nodeType)) out.add(p);
        return out;
    }

    public int nodeCount() { return nodes.size(); }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"'); i++;
                } else if (c == '"') {
                    inQuote = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == ',') {
                fields.add(sb.toString()); sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }

    private static int parseId(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return -1; }
    }
}
