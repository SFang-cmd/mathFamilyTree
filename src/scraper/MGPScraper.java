package scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.regex.*;

// Class that walks the Mathematics Genealogy Project (MGP) graph
// starting from Penn faculty seeds, going both up (advisors/ancestors)
// and down (students/descendants) via BFS, and writes the result to a CSV
public class MGPScraper {

//    base URL for any individual MGP profile page
    private static final String MGP_BASE = "https://genealogy.math.ndsu.nodak.edu/id.php?id=";

//    direction constants used to track which way each queued node should travel:
//    SEED goes both ways, UP follows advisors only, DOWN follows students only
    private static final int SEED = 0, UP = 1, DOWN = 2;

    /**
     * Main BFS entry point. Takes the list of Penn faculty [name, mgp_id] pairs
     * as seeds, walks the graph bidirectionally, and writes genealogy.csv.
     * We do this because the tree is way to big to fully traverse using BFS,
     * so we need to access direct ancestors on the professors only
     * (as it will show common ancestors, not related "aunts"/"uncles")
     * @return total number of nodes written to the CSV
     */
    public static int findAllAncestorsDescendants(List<String[]> seeds) throws Exception {
//        makes sure the data directory exists before trying to write to it
        new File("src/data").mkdirs();

//        queue entries are int[]{id, direction} so we know which way to travel from each node
        Queue<int[]> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();

//        seeds all Penn faculty as SEED direction so they go both up and down
        for (String[] pair : seeds) {
            try {
                int id = Integer.parseInt(pair[1]);
                visited.add(id);
                queue.add(new int[]{id, SEED});
            } catch (NumberFormatException ignored) {}
        }

//        track number of nodes explored
        int count = 0;
//        prints data to an output stream, namely a csv in this case
        try (PrintWriter pw = new PrintWriter("src/data/genealogy.csv")) {
            pw.println("mgp_id,name,institution,year,dissertation,advisor1_id,advisor1_name,advisor2_id,advisor2_name,advisor3_id,advisor3_name,node_type");

//            checks whether queue is empty
//            behavior is similar to bfs except we don't explore EVERYTHING
            while (!queue.isEmpty()) {
//                dissects each node
                int[] entry = queue.poll();
                int id  = entry[0];
                int dir = entry[1];
                System.out.println("Visiting node " + id + " (queue=" + queue.size() + ")...");
                try {
//                    fetch the MGP page for this node
                    Document doc = Jsoup.connect(MGP_BASE + id)
                            .userAgent("Mozilla/5.0").timeout(20000).get();
                    String[] row = fetchNode(doc, id);

//                    if page returned no usable data, skip and move on
                    if (row == null) {
                        Thread.sleep(300);
                        continue;
                    }

//                    label the node type based on which direction it was reached from
                    String nodeType = dir == SEED ? "seed" : dir == UP ? "ancestor" : "descendant";
//                    in order: name, institution, year, dissertation, advisor1_id, advisor1_name,
//                    advisor2_id, advisor2_name, advisor3_id, advisor3_name
                    pw.println(id + ","
                            + csvField(row[0]) + ","
                            + csvField(row[1]) + ","
                            + csvField(row[2]) + ","
                            + csvField(row[3]) + ","
                            + csvField(row[4]) + ","
                            + csvField(row[5]) + ","
                            + csvField(row[6]) + ","
                            + csvField(row[7]) + ","
                            + csvField(row[8]) + ","
                            + csvField(row[9]) + ","
                            + nodeType);
                    count++;

//                    if traveling up or starting from seed, enqueue all advisors as UP
                    if (dir == SEED || dir == UP) {
                        enqueue(row[4], UP, queue, visited);
                        enqueue(row[6], UP, queue, visited);
                        enqueue(row[8], UP, queue, visited);
                    }

//                    if traveling down or starting from seed, enqueue all students as DOWN
                    if (dir == SEED || dir == DOWN) {
                        for (String sid : fetchStudentIds(doc)) {
                            try {
                                enqueue(Integer.parseInt(sid), DOWN, queue, visited);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                } catch (Exception e) {
                    System.out.println("  Skipping " + id + ": " + e.getMessage());
                }
//                small sleep between requests so we don't get rate limited by MGP
                Thread.sleep(300);
            }
        }
        return count;
    }

    /**
     * String overload of enqueue, which parses the id string before delegating.
     * Used when pulling advisor IDs directly out of the fetchNode row array.
     * Advisors are in weird formats in the MGP, so we need to make sure we pull
     * all advisors (Whether they are labelled as "advisor" or "advisor 1" or "advisor 2" etc)
     */
    private static void enqueue(String idStr, int dir, Queue<int[]> queue, Set<Integer> visited) {
//        skip empty or null advisor slots (most nodes have fewer than 3 advisors)
        if (idStr == null || idStr.isEmpty()) {
            return;
        }
        try {
            enqueue(Integer.parseInt(idStr), dir, queue, visited);
        } catch (NumberFormatException ignored) {}
    }

    /**
     * Adds a node to the BFS queue if it hasn't been visited yet.
     * visited set prevents processing the same node twice from different lineages.
     */
    private static void enqueue(int id, int dir, Queue<int[]> queue, Set<Integer> visited) {
        if (!visited.contains(id)) {
            visited.add(id);
            queue.add(new int[]{id, dir});
        }
    }

    /**
     * Pulls all student IDs from an already-fetched MGP page.
     * Student links live inside table cells, which distinguishes them
     * from advisor links that appear in paragraph text.
     * @return list of MGP ID strings for the node's students
     */
    private static List<String> fetchStudentIds(Document doc) {
//        init list of student ids
        List<String> ids = new ArrayList<>();
//        student links live inside <td> elements, so this selector avoids picking up advisor links
        Elements studentLinks = doc.select("td a[href*=id.php]");
        for (Element link : studentLinks) {
            String sid = link.attr("href").replaceAll(".*id=", "").trim();
//            only add if it parses cleanly as an integer — skips any malformed hrefs
            try {
                Integer.parseInt(sid);
                ids.add(sid);
            } catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    /**
     * Parses a single MGP profile page and extracts all relevant fields.
     * Advisors are assigned by order of appearance (not by label number) so
     * that people with multiple degrees listing separate "Advisor 1:" entries
     * are handled correctly.
     * @return String[] of [name, institution, year, dissertation,
     *                      adv1Id, adv1Name, adv2Id, adv2Name, adv3Id, adv3Name]
     *         or null if the page has no usable name
     */
    static String[] fetchNode(Document doc, int id) {
        try {
//            name is always in the first h2 on the page
            Element h2 = doc.selectFirst("h2");
            String name = h2 != null ? h2.text().trim() : "";
//            if there's no name, this isn't a real profile page — skip it
            if (name.isEmpty()) {
                return null;
            }

//            institution is in a green-colored span (#006633); year follows it as text in the parent
            String institution = "", year = "";
            Element instSpan = doc.selectFirst("span[style*=006633]");
            if (instSpan != null) {
                institution = instSpan.text().trim();
                Matcher m = Pattern.compile("(\\d{4})").matcher(instSpan.parent().text());
                if (m.find()) {
                    year = m.group(1);
                }
            }

//            dissertation title has its own dedicated element id
            String dissertation = "";
            Element thesisEl = doc.getElementById("thesisTitle");
            if (thesisEl != null) {
                dissertation = thesisEl.text().trim();
            }

//            advisors are matched by order of appearance, not by number label,
//            so "Advisor 1:" appearing twice (two separate degrees) still maps correctly.
//            pattern covers all label variants seen on MGP: "Advisor", "Advisor 1/2/3",
//            and "Mentor" (some older entries use this instead of Advisor)
            String adv1Id = "", adv1Name = "", adv2Id = "", adv2Name = "", adv3Id = "", adv3Name = "";
            Pattern advPattern = Pattern.compile("(?:Advisor|Mentor)\\s*\\d*:\\s*<a href=\"id\\.php\\?id=(\\d+)\">(.*?)</a>");
            Matcher am = advPattern.matcher(doc.body().html());
            int advCount = 0;
            while (am.find() && advCount < 3) {
                advCount++;
                if (advCount == 1) {
                    adv1Id = am.group(1); adv1Name = am.group(2);
                } else if (advCount == 2) {
                    adv2Id = am.group(1); adv2Name = am.group(2);
                } else {
                    adv3Id = am.group(1); adv3Name = am.group(2);
                }
            }

            return new String[]{name, institution, year, dissertation, adv1Id, adv1Name, adv2Id, adv2Name, adv3Id, adv3Name};
        } catch (Exception e) {
            System.out.println("  fetchNode error for " + id + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Wraps a CSV field value in quotes if it contains a comma, quote, or newline.
     * Empty/null values are written as empty strings (no quotes).
     */
    private static String csvField(String s) {
//        empty fields are fine to write as-is
        if (s == null || s.isEmpty()) {
            return "";
        }
//        quote the field if it contains any character that would break CSV parsing
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
