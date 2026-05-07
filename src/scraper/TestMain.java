package scraper;

import java.util.ArrayList;
import java.util.List;

// Lightweight test runner used to verify the scraper pipeline on a single known seed
// before committing to the full 20-40 minute run against all Penn faculty.
// Jonathan Block was chosen because his lineage is well-documented: ancestors trace
// back to Euler and Khayyam, and his student Andrey Lazarev has known descendants
// we can cross-check against the MGP website manually.
public class TestMain {

    /**
     * Runs the BFS pipeline with only Jonathan Block as the seed and
     * prints the total node count on completion.
     */
    public static void main(String[] args) throws Exception {
//        hardcode just Jonathan Block (MGP ID 23182) as the only seed for this test run
        List<String[]> seeds = new ArrayList<>();
        seeds.add(new String[]{"Jonathan L. Block", "23182"});

//        runs the same BFS as the full pipeline — output still goes to genealogy.csv
        int total = MGPScraper.findAllAncestorsDescendants(seeds);
        System.out.println("Test done. " + total + " nodes written to src/data/genealogy.csv");
    }
}
