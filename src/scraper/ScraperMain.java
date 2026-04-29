package scraper;

import java.util.List;

public class ScraperMain {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Step 1: Scraping Penn Math faculty and looking up MGP IDs ===");
        List<String[]> seeds = PennFacultyScraper.run();

        System.out.println("\n=== Step 2: BFS walk through advisor genealogy ===");
        int total = MGPScraper.run(seeds);

        System.out.println("\nDone. " + total + " nodes written to src/data/genealogy.csv");
        System.out.println("Faculty CSV:   src/data/penn_faculty.csv");
        System.out.println("Genealogy CSV: src/data/genealogy.csv");
    }
}
