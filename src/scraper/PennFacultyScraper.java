package scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.PrintWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class PennFacultyScraper {

    private static final String[] PENN_URLS = {
        "https://www.math.upenn.edu/people/standing-faculty",
        "https://www.math.upenn.edu/people/associated-faculty",
        "https://www.math.upenn.edu/people/lecturers-and-postdocs",
        "https://www.math.upenn.edu/people/visiting-faculty"
    };

    private static final String MGP_SEARCH = "https://genealogy.math.ndsu.nodak.edu/query-prep.php";
    private static final String MGP_RESULTS = "https://genealogy.math.ndsu.nodak.edu/results.php";

    /** Returns list of [name, mgp_id] pairs. Writes penn_faculty.csv. */
    public static List<String[]> run() throws Exception {
        List<String> names = fetchAllNames();
        System.out.println("Found " + names.size() + " faculty across all 4 pages.");

        new File("src/data").mkdirs();
        List<String[]> results = new ArrayList<>();

        try (PrintWriter pw = new PrintWriter("src/data/penn_faculty.csv")) {
            pw.println("name,mgp_id");
            for (String name : names) {
                String[] parts = splitName(name);
                if (parts == null) continue;
                String lastName = parts[1];
                // Strip middle initials/names: keep only the first word of the first-name token
                String firstName = parts[0].split("\\s+")[0];
                try {
                    int id = searchMGPWithFallback(firstName, lastName);
                    if (id > 0) {
                        pw.println("\"" + name + "\"," + id);
                        results.add(new String[]{name, String.valueOf(id)});
                        System.out.println("  Matched: " + name + " -> " + id);
                    } else {
                        System.out.println("  No MGP match: " + name);
                    }
                    Thread.sleep(300);
                } catch (Exception e) {
                    System.out.println("  Error looking up " + name + ": " + e.getMessage());
                }
            }
        }
        System.out.println("Matched " + results.size() + " of " + names.size() + " faculty in MGP.");
        return results;
    }

    private static List<String> fetchAllNames() {
        List<String> names = new ArrayList<>();
        for (String url : PENN_URLS) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();
                Elements h2s = doc.select("div.views-field span.field-content h2");
                for (Element h : h2s) {
                    String name = h.text().trim().replaceAll("\\s+", " ");
                    if (!name.isEmpty()) names.add(name);
                }
                System.out.println("  " + url + " -> " + h2s.size() + " names");
            } catch (Exception e) {
                System.out.println("  Failed to fetch " + url + ": " + e.getMessage());
            }
        }
        return names;
    }

    private static String stripDiacritics(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                         .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Multi-step MGP search:
     *   1. firstName (stripped) + lastName
     *   2. lastName only — accepts result only if exactly one match
     */
    private static int searchMGPWithFallback(String firstName, String lastName) throws Exception {
        String cleanFirst = stripDiacritics(firstName);
        String cleanLast  = stripDiacritics(lastName);

        int id = searchMGP(cleanFirst, cleanLast);
        if (id > 0) return id;

        Thread.sleep(300);

        // Step 2: last name only (handles nicknames like Ted -> Theodore)
        return searchMGPLastNameOnly(cleanLast);
    }

    /**
     * MGP stores the query in a PHP session via a POST to query-prep.php;
     * the results are then available via a GET to results.php using that session cookie.
     */
    private static Document postAndGetResults(String firstName, String lastName) throws Exception {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("chrono", "0");
        data.put("given_name", firstName);
        data.put("family_name", lastName);
        data.put("other_names", "");
        data.put("school", "");
        data.put("year", "");
        data.put("thesis", "");
        data.put("code", "");
        data.put("country", "");

        Map<String, String> cookies = Jsoup.connect(MGP_SEARCH)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .data(data)
                .method(org.jsoup.Connection.Method.POST)
                .execute()
                .cookies();

        return Jsoup.connect(MGP_RESULTS)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .cookies(cookies)
                .get();
    }

    /**
     * Search MGP by first + last name. Returns the first matching MGP ID, or -1 if not found.
     */
    private static int searchMGP(String firstName, String lastName) throws Exception {
        Document results = postAndGetResults(firstName, lastName);
        Element link = results.selectFirst("a[href~=id\\.php\\?id=\\d+]");
        if (link == null) return -1;
        return Integer.parseInt(link.attr("href").replaceAll(".*id=", "").trim());
    }

    /**
     * Search MGP by last name only. Returns the MGP ID if exactly one result is found,
     * or -1 if there are zero or multiple results (too ambiguous).
     */
    private static int searchMGPLastNameOnly(String lastName) throws Exception {
        Document results = postAndGetResults("", lastName);
        Elements links = results.select("a[href~=id\\.php\\?id=\\d+]");
        if (links.size() != 1) return -1;  // 0 = no match, >1 = ambiguous
        return Integer.parseInt(links.first().attr("href").replaceAll(".*id=", "").trim());
    }

    /** Split "First Middle Last" -> ["First Middle", "Last"]. */
    private static String[] splitName(String full) {
        full = full.trim();
        int sp = full.lastIndexOf(' ');
        if (sp < 0) return null;
        return new String[]{full.substring(0, sp).trim(), full.substring(sp + 1).trim()};
    }
}
