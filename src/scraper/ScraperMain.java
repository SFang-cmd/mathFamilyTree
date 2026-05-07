package scraper;

import java.util.List;

// Entry point for the full scraper pipeline.
// Step 1 scrapes Penn Math faculty pages and looks each professor up in MGP.
// Step 2 does a bidirectional BFS from those seeds through the MGP graph,
// collecting ancestors (advisors) and descendants (students) into genealogy.csv
public class ScraperMain {

    /**
     * Runs the full two-step pipeline end to end.
     * Expect this to take 20-40 minutes on the full faculty list
     * due to the 300ms sleep between MGP requests.
     */
    public static void main(String[] args) throws Exception {
//        step 1: hit the 4 Penn Math pages, find each professor in MGP, write penn_faculty.csv
        System.out.println("=== Step 1: Scraping Penn Math faculty and looking up MGP IDs ===");
        List<String[]> seeds = PennFacultyScraper.findValidProfs();

//        step 2: BFS from each seed both up (advisors) and down (students), write genealogy.csv
        System.out.println("\n=== Step 2: BFS walk through advisor genealogy ===");
        int total = MGPScraper.findAllAncestorsDescendants(seeds);

//        final output summary so we know where to find the files
        System.out.println("\nDone. " + total + " nodes written to src/data/genealogy.csv");
        System.out.println("Faculty CSV:   src/data/penn_faculty.csv");
        System.out.println("Genealogy CSV: src/data/genealogy.csv");
    }
}
