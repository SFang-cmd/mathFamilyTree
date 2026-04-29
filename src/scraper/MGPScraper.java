package scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MGPScraper {

    private static final String MGP_BASE = "https://genealogy.math.ndsu.nodak.edu/id.php?id=";
    private static final int SEED = 0, UP = 1, DOWN = 2;

    public static int run(List<String[]> seeds) throws Exception {
        new File("src/data").mkdirs();

        Queue<int[]> queue = new LinkedList<>();
        Set<Integer> visited = new HashSet<>();

        for (String[] pair : seeds) {
            try {
                int id = Integer.parseInt(pair[1]);
                visited.add(id);
                queue.add(new int[]{id, SEED});
            } catch (NumberFormatException ignored) {}
        }

        int count = 0;
        try (PrintWriter pw = new PrintWriter("src/data/genealogy.csv")) {
            pw.println("mgp_id,name,institution,year,dissertation,advisor1_id,advisor1_name,advisor2_id,advisor2_name,advisor3_id,advisor3_name,node_type");

            while (!queue.isEmpty()) {
                int[] entry = queue.poll();
                int id = entry[0];
                int dir = entry[1];
                System.out.println("Visiting node " + id + " (queue=" + queue.size() + ")...");
                try {
                    Document doc = Jsoup.connect(MGP_BASE + id)
                            .userAgent("Mozilla/5.0").timeout(20000).get();
                    String[] row = fetchNode(doc, id);
                    if (row == null) { Thread.sleep(300); continue; }

                    String nodeType = dir == SEED ? "seed" : dir == UP ? "ancestor" : "descendant";
                    pw.println(id + ","
                            + csvField(row[0]) + ","   // name
                            + csvField(row[1]) + ","   // institution
                            + csvField(row[2]) + ","   // year
                            + csvField(row[3]) + ","   // dissertation
                            + csvField(row[4]) + ","   // advisor1_id
                            + csvField(row[5]) + ","   // advisor1_name
                            + csvField(row[6]) + ","   // advisor2_id
                            + csvField(row[7]) + ","   // advisor2_name
                            + csvField(row[8]) + ","   // advisor3_id
                            + csvField(row[9]) + ","   // advisor3_name
                            + nodeType);
                    count++;

                    if (dir == SEED || dir == UP) {
                        enqueue(row[4], UP, queue, visited);
                        enqueue(row[6], UP, queue, visited);
                        enqueue(row[8], UP, queue, visited);
                    }
                    if (dir == SEED || dir == DOWN) {
                        for (String sid : fetchStudentIds(doc)) {
                            try { enqueue(Integer.parseInt(sid), DOWN, queue, visited); }
                            catch (NumberFormatException ignored) {}
                        }
                    }

                } catch (Exception e) {
                    System.out.println("  Skipping " + id + ": " + e.getMessage());
                }
                Thread.sleep(300);
            }
        }
        return count;
    }

    private static void enqueue(String idStr, int dir, Queue<int[]> queue, Set<Integer> visited) {
        if (idStr == null || idStr.isEmpty()) return;
        try { enqueue(Integer.parseInt(idStr), dir, queue, visited); }
        catch (NumberFormatException ignored) {}
    }

    private static void enqueue(int id, int dir, Queue<int[]> queue, Set<Integer> visited) {
        if (!visited.contains(id)) {
            visited.add(id);
            queue.add(new int[]{id, dir});
        }
    }

    private static List<String> fetchStudentIds(Document doc) {
        List<String> ids = new ArrayList<>();
        Elements studentLinks = doc.select("td a[href*=id.php]");
        for (Element link : studentLinks) {
            String sid = link.attr("href").replaceAll(".*id=", "").trim();
            try { Integer.parseInt(sid); ids.add(sid); }
            catch (NumberFormatException ignored) {}
        }
        return ids;
    }

    static String[] fetchNode(Document doc, int id) {
        try {
            Element h2 = doc.selectFirst("h2");
            String name = h2 != null ? h2.text().trim() : "";
            if (name.isEmpty()) return null;

            String institution = "", year = "";
            Element instSpan = doc.selectFirst("span[style*=006633]");
            if (instSpan != null) {
                institution = instSpan.text().trim();
                Matcher m = Pattern.compile("(\\d{4})").matcher(instSpan.parent().text());
                if (m.find()) year = m.group(1);
            }

            String dissertation = "";
            Element thesisEl = doc.getElementById("thesisTitle");
            if (thesisEl != null) dissertation = thesisEl.text().trim();

            String adv1Id="", adv1Name="", adv2Id="", adv2Name="", adv3Id="", adv3Name="";
            Pattern advPattern = Pattern.compile("Advisor\\s*\\d*:\\s*<a href=\"id\\.php\\?id=(\\d+)\">(.*?)</a>");
            Matcher am = advPattern.matcher(doc.body().html());
            int advCount = 0;
            while (am.find() && advCount < 3) {
                advCount++;
                if      (advCount == 1) { adv1Id = am.group(1); adv1Name = am.group(2); }
                else if (advCount == 2) { adv2Id = am.group(1); adv2Name = am.group(2); }
                else                    { adv3Id = am.group(1); adv3Name = am.group(2); }
            }

            return new String[]{name, institution, year, dissertation, adv1Id, adv1Name, adv2Id, adv2Name, adv3Id, adv3Name};
        } catch (Exception e) {
            System.out.println("  fetchNode error for " + id + ": " + e.getMessage());
            return null;
        }
    }

    private static String csvField(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
