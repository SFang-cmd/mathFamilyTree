package tree;

public class Person {
    public final int    mgpId;
    public final String name;
    public final String institution;
    public final String year;
    public final String dissertation;
    public final int    advisor1Id;
    public final String advisor1Name;
    public final int    advisor2Id;
    public final String advisor2Name;
    public final int    advisor3Id;
    public final String advisor3Name;
    public final String nodeType;   // "seed" | "ancestor" | "descendant"

    public Person(int mgpId, String name, String institution, String year,
                  String dissertation,
                  int advisor1Id, String advisor1Name,
                  int advisor2Id, String advisor2Name,
                  int advisor3Id, String advisor3Name,
                  String nodeType) {
        this.mgpId        = mgpId;
        this.name         = name;
        this.institution  = institution;
        this.year         = year;
        this.dissertation = dissertation;
        this.advisor1Id   = advisor1Id;
        this.advisor1Name = advisor1Name;
        this.advisor2Id   = advisor2Id;
        this.advisor2Name = advisor2Name;
        this.advisor3Id   = advisor3Id;
        this.advisor3Name = advisor3Name;
        this.nodeType     = nodeType;
    }

    @Override
    public String toString() {
        return name + " (" + (year.isEmpty() ? "?" : year) + ")";
    }
}
