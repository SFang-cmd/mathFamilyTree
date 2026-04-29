package scraper;

import java.util.List;

public class TestMain {
    public static void main(String[] args) throws Exception {
        // Jonathan Block, MGP ID 23182 — the only seed for this test run
        List<String[]> seeds = new java.util.ArrayList<>();
        seeds.add(new String[]{"Jonathan L. Block", "23182"});
        int total = MGPScraper.run(seeds);
        System.out.println("Test done. " + total + " nodes written to src/data/genealogy.csv");
    }
}
