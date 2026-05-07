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

//Class that scrapes the Penn website for current math professors
//then, compares to the math genealogy tree to ensure the professor
//exists in the database. If so, adds it, otherwise, does not add
public class PennFacultyScraper {

//    List of all active/semi-active Math professors at Penn
//    split into standing faculty, associated faculty,
//    lecturers and postdocs, and visiting faculty
    private static final String[] PENN_URLS = {
        "https://www.math.upenn.edu/people/standing-faculty",
        "https://www.math.upenn.edu/people/associated-faculty",
        "https://www.math.upenn.edu/people/lecturers-and-postdocs",
        "https://www.math.upenn.edu/people/visiting-faculty"
    };

//    because the website does not have an existing API, we need to find a JSoup workaround
    private static final String MGP_SEARCH = "https://genealogy.math.ndsu.nodak.edu/query-prep.php";
    private static final String MGP_RESULTS = "https://genealogy.math.ndsu.nodak.edu/results.php";

    /**
     * Generates [name, mgp_id] pairs for
     * penn professors that exist in the family tree
     * and writes it to penn_faculty.csv
     * @return list of [name, mgp_id]
     */
    public static List<String[]> findValidProfs() throws Exception {
//
        List<String> names = fetchAllNames();
        System.out.println("Found " + names.size() + " faculty across all 4 pages.");

        new File("src/data").mkdirs();
        List<String[]> results = new ArrayList<>();

        try (PrintWriter pw = new PrintWriter("src/data/penn_faculty.csv")) {
            pw.println("name,mgp_id");
            for (String name : names) {
                String[] parts = splitName(name);
                if (parts == null) {
                    continue;
                }
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

    /**
     * grabs all math professor names from the 4 URLs that contain
     * math professors at Penn
     * @return list of Penn Professors associated with the Mathematics department
     */
    private static List<String> fetchAllNames() {
//        init name list
        List<String> names = new ArrayList<>();
//        loops through PENN_URLS constants
        for (String url : PENN_URLS) {
//            tries to create a user agent to access the specific Penn URL page
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(15000)
                        .get();
//                selects specific header elements using jsoup
                Elements h2s = doc.select("div.views-field span.field-content h2");
//                loops through elements and extracts the name using regex
                for (Element h : h2s) {
                    String name = h.text().trim().replaceAll("\\s+", " ");
//                    checks that name exists and is not a dummy section
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
//                for logging purposes
                System.out.println("  " + url + " -> " + h2s.size() + " names");
            } catch (Exception e) {
//                if fetch failes, prints out error
                System.out.println("  Failed to fetch " + url + ": " + e.getMessage());
            }
        }
        return names;
    }

//    helper function to strip diacritics from function names
    private static String stripDiacritics(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                         .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    /**
     * Multi-step MGP search that first checks the stripped firstName of the prof
     * along with the lastName. If this doesn't work (due to names like Ted being
     * represented as "Theodore" on the tree), then we try lastNames only,
     * which only accepts result if there is exactly one match
     * @return Genealogy tree ID (MGP ID from the website)
     */
    private static int searchMGPWithFallback(String firstName, String lastName) throws Exception {
//        cleans all diacritics on names, since MGP doesn't have this
        String cleanFirst = stripDiacritics(firstName);
        String cleanLast  = stripDiacritics(lastName);

//        searches MGP for the name and returns id of professor
        int id = searchMGP(cleanFirst, cleanLast);
//        if id is a valid id, return it
        if (id > 0) {
            return id;
        }

//        sleeps to prevent rate limiting
        Thread.sleep(300);

        // if still doesn't exist, we try last name only (handles nicknames like Ted vs. Theodore)
        return searchMGPLastNameOnly(cleanLast);
    }

    /**
     * MGP stores the query in a PHP thing, grabs the data from php
     * after requesting the data itself
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

//        jank stuff stackoverflow said would work that I don't fully understand
        Map<String, String> cookies = Jsoup.connect(MGP_SEARCH)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .data(data)
                .method(org.jsoup.Connection.Method.POST)
                .execute()
                .cookies();

//        returns the result from the cookies (..?) that come from the result
//        of the php result
        return Jsoup.connect(MGP_RESULTS)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .cookies(cookies)
                .get();
    }

    /**
     * Search MGP by first and last name
     * @return the first matching MGP ID, or -1 if not found.
     */
    private static int searchMGP(String firstName, String lastName) throws Exception {
//        searches using cleaned first and last name combined within the MGP database
        Document results = postAndGetResults(firstName, lastName);
//        checks whether the link from the url works using regex
        Element link = results.selectFirst("a[href~=id\\.php\\?id=\\d+]");
//        if link value doesn't exist, error out
        if (link == null) {
            return -1;
        }
//        otherwise, get the value which corresponds to MGP ID
        return Integer.parseInt(link.attr("href").replaceAll(".*id=", "").trim());
    }

    /**
     * Search MGP by last name only
     * @return the MGP ID if exactly one result is found,
     * or -1 if there are zero or multiple results (too ambiguous).
     */
    private static int searchMGPLastNameOnly(String lastName) throws Exception {
//        does the same thing as first and last MGP search, but just
//        searches using cleaned last name only
        Document results = postAndGetResults("", lastName);
        Elements links = results.select("a[href~=id\\.php\\?id=\\d+]");
        if (links.size() != 1) return -1;  // 0 = no match, >1 = ambiguous
        return Integer.parseInt(links.first().attr("href").replaceAll(".*id=", "").trim());
    }

    /**
     * Helper method that splits the full name from the Penn website into the first and last
     */
    private static String[] splitName(String full) {
//        removes whitespace
        full = full.trim();
//        finds space index
        int sp = full.lastIndexOf(' ');
//        if space doesn't exist (-1), then return null
        if (sp < 0) {
            return null;
        }
//        return the 2 separate substrings, trimmed for no whitespace
        return new String[]{full.substring(0, sp).trim(), full.substring(sp + 1).trim()};
    }
}
