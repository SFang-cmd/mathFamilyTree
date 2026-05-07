package tree;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

/**
 * Opens a Swing window showing the academic genealogy as a top-down tree.
 * Scroll to zoom, drag to pan, click a node for details.
 *
 * Run: java -cp target/hw5-tree.jar tree.TreeVisualizer
 */
public class TreeVisualizer {

    static final int DX  = 80;   // horizontal pixels per leaf slot
    static final int DY  = 90;   // vertical pixels per depth level
    static final int R   = 18;   // node circle radius
    static final int PAD = 60;   // canvas edge padding

    public static void main(String[] args) throws Exception {
        System.out.println("Loading genealogy.csv...");
        AcademicGraph graph = AcademicGraph.loadFromCSV("src/data/genealogy.csv");

        // ── 1. Build spanning tree (primary advisor = parent) ─────────────────
        Map<Integer, Integer>       parent   = new HashMap<>();
        Map<Integer, List<Integer>> children = new LinkedHashMap<>();
        for (Person p : graph.allPersons())
            children.put(p.mgpId, new ArrayList<>());

        for (Person p : graph.allPersons()) {
            int par = -1;
            for (int id : new int[]{p.advisor1Id, p.advisor2Id, p.advisor3Id})
                if (id > 0 && graph.hasNode(id)) { par = id; break; }
            if (par != -1) {
                parent.put(p.mgpId, par);
                children.get(par).add(p.mgpId);
            }
        }

        // ── 2. Compute positions (leaf-counting layout, no crossings) ─────────
        Map<Integer, Double>  lx      = new HashMap<>();
        Map<Integer, Integer> depth   = new HashMap<>();
        double[]              counter = {0};

        for (Person p : graph.allPersons())
            if (!parent.containsKey(p.mgpId))
                assignPos(p.mgpId, 0, children, lx, depth, counter);

        double minX   = lx.values().stream().mapToDouble(v -> v).min().orElse(0);
        int    maxDep = depth.values().stream().mapToInt(v -> v).max().orElse(0);
        int    totalW = (int)((lx.values().stream().mapToDouble(v -> v).max().orElse(0) - minX) * DX) + 2*PAD;
        int    totalH = maxDep * DY + 2*PAD;

        // Convert logical positions to pixel coords
        Map<Integer, int[]> pos = new HashMap<>();
        for (Person p : graph.allPersons())
            pos.put(p.mgpId, new int[]{
                (int)((lx.get(p.mgpId) - minX) * DX) + PAD,
                depth.get(p.mgpId) * DY + PAD
            });

        // ── 3. Swing UI ───────────────────────────────────────────────────────
        // Arrays used so anonymous inner classes can mutate them
        double[] scale   = {1.0};
        double[] off     = {0, 0};       // pan offset (x, y)
        int[]    drag    = {0, 0};       // mouse press position
        double[] dragOff = {0, 0};       // offset at press time
        Person[] sel     = {null};       // currently selected node

        JLabel statusBar = new JLabel("  Click a node to see details");
        statusBar.setForeground(Color.WHITE);
        statusBar.setBackground(new Color(30, 41, 59));
        statusBar.setOpaque(true);
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Background
                g.setColor(new Color(17, 24, 39));
                g.fillRect(0, 0, getWidth(), getHeight());

                // Save base transform, then apply pan/zoom for the world
                AffineTransform base = g.getTransform();
                g.translate(off[0], off[1]);
                g.scale(scale[0], scale[0]);

                // Draw edges as elbow connectors (parent → midpoint → child)
                g.setStroke(new BasicStroke(0.8f));
                g.setColor(new Color(55, 65, 81));
                for (Map.Entry<Integer, Integer> e : parent.entrySet()) {
                    int[] cp = pos.get(e.getKey());    // child position
                    int[] pp = pos.get(e.getValue());   // parent position
                    int midY = (pp[1] + cp[1]) / 2;
                    g.drawLine(pp[0], pp[1] + R, pp[0], midY);   // down from parent
                    g.drawLine(pp[0], midY,       cp[0], midY);   // across
                    g.drawLine(cp[0], midY,       cp[0], cp[1] - R); // down to child
                }

                // Draw nodes
                g.setFont(new Font("SansSerif", Font.PLAIN, 9));
                FontMetrics fm = g.getFontMetrics();

                for (Person p : graph.allPersons()) {
                    int[] np = pos.get(p.mgpId);

                    // Filled circle
                    g.setColor(nodeColor(p.nodeType));
                    g.fillOval(np[0]-R, np[1]-R, 2*R, 2*R);

                    // White ring for Penn faculty
                    if ("seed".equals(p.nodeType)) {
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(1.5f));
                        g.drawOval(np[0]-R, np[1]-R, 2*R, 2*R);
                        g.setStroke(new BasicStroke(0.8f));
                    }

                    // Yellow highlight for selected node
                    if (sel[0] != null && sel[0].mgpId == p.mgpId) {
                        g.setColor(Color.YELLOW);
                        g.setStroke(new BasicStroke(2.0f));
                        g.drawOval(np[0]-R-2, np[1]-R-2, 2*R+4, 2*R+4);
                        g.setStroke(new BasicStroke(0.8f));
                    }

                    // Last-name label inside circle
                    String label = lastName(p.name);
                    g.setColor(Color.WHITE);
                    g.drawString(label, np[0] - fm.stringWidth(label)/2, np[1] + fm.getAscent()/2 - 1);
                }

                // Restore screen coords and draw the legend overlay
                g.setTransform(base);
                drawLegend(g);
            }
        };

        // Scroll to zoom (getPreciseWheelRotation works correctly on Mac trackpads)
        canvas.addMouseWheelListener(e -> {
            double f = e.getPreciseWheelRotation() < 0 ? 1.12 : 1.0 / 1.12;
            off[0] = (off[0] - e.getX()) * f + e.getX();
            off[1] = (off[1] - e.getY()) * f + e.getY();
            scale[0] *= f;
            canvas.repaint();
        });

        // Click to select, drag to pan
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                drag[0]    = e.getX();   drag[1]    = e.getY();
                dragOff[0] = off[0];     dragOff[1] = off[1];

                // Convert screen click to world coordinates, then hit-test nodes
                double wx = (e.getX() - off[0]) / scale[0];
                double wy = (e.getY() - off[1]) / scale[0];
                sel[0] = null;
                for (Person p : graph.allPersons()) {
                    int[] np = pos.get(p.mgpId);
                    double dx = wx - np[0], dy = wy - np[1];
                    if (dx*dx + dy*dy <= (double)R*R) { sel[0] = p; break; }
                }
                if (sel[0] != null) {
                    String yr   = sel[0].year.isEmpty()        ? "—" : sel[0].year;
                    String inst = sel[0].institution.isEmpty() ? "—" : sel[0].institution;
                    statusBar.setText(String.format("  %s  ·  %s  ·  PhD %s  ·  [%s]",
                            sel[0].name, inst, yr, sel[0].nodeType));
                }
                canvas.repaint();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                off[0] = dragOff[0] + (e.getX() - drag[0]);
                off[1] = dragOff[1] + (e.getY() - drag[1]);
                canvas.repaint();
            }
        });

        // ── 4. Show window ────────────────────────────────────────────────────
        JFrame frame = new JFrame("Penn Math Academic Genealogy");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(canvas,    BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);  // center on screen
        frame.setVisible(true);

        // Fit entire tree into the window once the canvas knows its size
        SwingUtilities.invokeLater(() -> {
            int w = canvas.getWidth(), h = canvas.getHeight();
            scale[0] = Math.min((double) w / totalW, (double) h / totalH) * 0.92;
            off[0]   = (w - totalW * scale[0]) / 2;
            off[1]   = (h - totalH * scale[0]) / 2;
            canvas.repaint();
        });
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    /** Post-order DFS: leaves get sequential x slots; parents center over children. */
    static void assignPos(int node, int dep,
                          Map<Integer, List<Integer>> children,
                          Map<Integer, Double> x, Map<Integer, Integer> y,
                          double[] counter) {
        y.put(node, dep);
        List<Integer> ch = children.get(node);
        if (ch == null || ch.isEmpty()) {
            x.put(node, counter[0]++);
        } else {
            for (int child : ch)
                assignPos(child, dep + 1, children, x, y, counter);
            x.put(node, (x.get(ch.get(0)) + x.get(ch.get(ch.size()-1))) / 2.0);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    static void drawLegend(Graphics2D g) {
        int x = 16, y = 16;
        g.setColor(new Color(30, 41, 59, 220));
        g.fillRoundRect(x, y, 175, 96, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString("Penn Math Genealogy", x + 12, y + 22);

        int[]    oy   = {y+42, y+62, y+82};
        Color[]  cols = {new Color(59,130,246), new Color(34,197,94), new Color(249,115,22)};
        String[] lbls = {"Penn Faculty", "Ancestor", "Descendant"};
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int i = 0; i < 3; i++) {
            g.setColor(cols[i]);
            g.fillOval(x + 12, oy[i] - 9, 12, 12);
            g.setColor(new Color(220, 220, 220));
            g.drawString(lbls[i], x + 30, oy[i]);
        }
    }

    static Color nodeColor(String type) {
        switch (type) {
            case "seed":       return new Color(59, 130, 246);   // blue
            case "ancestor":   return new Color(34, 197, 94);    // green
            case "descendant": return new Color(249, 115, 22);   // orange
            default:           return new Color(107, 114, 128);
        }
    }

    static String lastName(String name) {
        String[] parts = name.trim().split("\\s+");
        String s = parts[parts.length - 1];
        return s.length() > 10 ? s.substring(0, 9) + "…" : s;
    }
}
