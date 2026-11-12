package assignment;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class BVHDeepTest {

    private BVH bvh;
    /**
     * coordinates (x1,y1,x2,y2,x3,y3) of every triangle made by tri(), used by the
     * oracles.
     */
    private final Map<Shape, int[]> coords = new IdentityHashMap<>();

    private BVH create() {
        return new BoundedVolumeHierarchy();
    }

    @BeforeEach
    void init() {
        bvh = create();
    }

    private Triangle tri(int x1, int y1, int x2, int y2, int x3, int y3) {
        Triangle t = new Triangle(new Point(x1, y1), new Point(x2, y2), new Point(x3, y3));
        coords.put(t, new int[] { x1, y1, x2, y2, x3, y3 });
        return t;
    }

    private static Point2D.Double p(double x, double y) {
        return new Point2D.Double(x, y);
    }

    private static Vector2D v(double x, double y) {
        return new Vector2D(x, y);
    }

    private static List<Shape> L(Shape... s) {
        return new ArrayList<>(Arrays.asList(s));
    }

    private Triangle randTri(int i, Random r) {
        int cx = (i % 15) * 20, cy = (i / 15) * 20;
        while (true) {
            int[] c = new int[6];
            for (int k = 0; k < 6; k += 2) {
                c[k] = cx + 2 * r.nextInt(8);
                c[k + 1] = cy + 2 * r.nextInt(8);
            }
            long cr = (long) (c[2] - c[0]) * (c[5] - c[1]) - (long) (c[3] - c[1]) * (c[4] - c[0]);
            if (Math.abs(cr) >= 20) {
                return tri(c[0], c[1], c[2], c[3], c[4], c[5]);
            }
        }
    }

    private List<Shape> shapes(int from, int to, Random r) {
        List<Shape> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(randTri(i, r));
        }
        return out;
    }

    // ----- canonical keys so leaves printed by toString can be matched to shapes
    // -----
    private static String fmt(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    private static String keyOf(double[] n) {
        double[][] pts = { { n[0], n[1] }, { n[2], n[3] }, { n[4], n[5] } };
        Arrays.sort(pts, (a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
        StringBuilder sb = new StringBuilder();
        for (double[] q : pts) {
            sb.append('(').append(fmt(q[0])).append(',').append(fmt(q[1])).append(')');
        }
        return sb.toString();
    }

    private String keyOf(Shape s) {
        int[] c = coords.get(s);
        assertNotNull(c, "test bug: shape was not created by tri()");
        double[] d = new double[6];
        for (int i = 0; i < 6; i++) {
            d[i] = c[i];
        }
        return keyOf(d);
    }

    private List<String> expectedKeys(Collection<? extends Shape> shapes) {
        List<String> out = new ArrayList<>();
        for (Shape s : shapes) {
            out.add(keyOf(s));
        }
        Collections.sort(out);
        return out;
    }

    // ----- toString parser -----
    private static final String NUM = "-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?";
    private static final String PT = "\\(\\s*" + NUM + "\\s*,\\s*" + NUM + "\\s*\\)";
    private static final Pattern INTERNAL = Pattern.compile("\\[\\s*" + PT + "\\s*,\\s*" + PT + "\\s*\\]");
    // spec text shows commas between leaf points, spec example does not: accept
    // both
    private static final Pattern LEAF = Pattern.compile(PT + "\\s*,?\\s*" + PT + "\\s*,?\\s*" + PT);
    private static final Pattern NUMBER = Pattern.compile(NUM);

    private static final class Node {
        final int depth;
        final boolean leaf;
        final double[] nums;
        final String line;
        final List<Node> kids = new ArrayList<>();

        Node(int depth, boolean leaf, double[] nums, String line) {
            this.depth = depth;
            this.leaf = leaf;
            this.nums = nums;
            this.line = line;
        }

        int height() {
            int h = 0;
            for (Node k : kids) {
                h = Math.max(h, k.height() + 1);
            }
            return h;
        }

        int leaves() {
            if (leaf) {
                return 1;
            }
            int s = 0;
            for (Node k : kids) {
                s += k.leaves();
            }
            return s;
        }

        /** {minX, minY, maxX, maxY} */
        double[] box() {
            if (!leaf) {
                return nums;
            }
            return new double[] { Math.min(nums[0], Math.min(nums[2], nums[4])),
                    Math.min(nums[1], Math.min(nums[3], nums[5])),
                    Math.max(nums[0], Math.max(nums[2], nums[4])),
                    Math.max(nums[1], Math.max(nums[3], nums[5])) };
        }

        void leafKeys(List<String> out) {
            if (leaf) {
                out.add(keyOf(nums));
            }
            for (Node k : kids) {
                k.leafKeys(out);
            }
        }
    }

    private static List<String> leafKeysOf(Node n) {
        List<String> out = new ArrayList<>();
        n.leafKeys(out);
        Collections.sort(out);
        return out;
    }

    private static Node parse(String text, String ctx) {
        Node root = null;
        Deque<Node> stack = new ArrayDeque<>();
        for (String raw : text.split("\\R")) {
            if (raw.trim().isEmpty()) {
                continue;
            }
            assertFalse(raw.startsWith(" "), "indentation must use tabs, not spaces: '" + raw + "'" + ctx);
            int depth = 0;
            while (depth < raw.length() && raw.charAt(depth) == '\t') {
                depth++;
            }
            String content = raw.trim();
            boolean leaf = !content.startsWith("[");
            assertTrue((leaf ? LEAF : INTERNAL).matcher(content).matches(),
                    "line does not match the spec's " + (leaf ? "leaf" : "internal-node") + " format: '" + content + "'"
                            + ctx);
            List<Double> vals = new ArrayList<>();
            java.util.regex.Matcher m = NUMBER.matcher(content);
            while (m.find()) {
                vals.add(Double.parseDouble(m.group()));
            }
            double[] nums = new double[vals.size()];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = vals.get(i);
            }
            Node n = new Node(depth, leaf, nums, content);
            while (!stack.isEmpty() && stack.peek().depth >= depth) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                assertNull(root, "toString printed more than one root line: '" + content + "'" + ctx);
                assertEquals(0, depth, "root must have depth 0 (no tabs): '" + content + "'" + ctx);
                root = n;
            } else {
                Node parent = stack.peek();
                assertEquals(parent.depth + 1, depth, "bad indentation (depth jumps) at '" + content + "'" + ctx);
                assertFalse(parent.leaf, "a leaf has a child line: '" + content + "'" + ctx);
                parent.kids.add(n);
            }
            stack.push(n);
        }
        return root;
    }

    private static void checkNode(Node n, String ctx, boolean checkAvl) {
        if (n.leaf) {
            return;
        }
        assertEquals(2, n.kids.size(), "internal node must have exactly 2 children: '" + n.line + "'" + ctx);
        double[] a = n.kids.get(0).box(), b = n.kids.get(1).box();
        double[] want = { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3]) };
        assertArrayEquals(want, n.nums, 1e-9,
                "bounding box of '" + n.line + "' is not the smallest rectangle around its children"
                        + ctx);
        if (checkAvl) {
            int h0 = n.kids.get(0).height(), h1 = n.kids.get(1).height();
            assertTrue(Math.abs(h0 - h1) <= 1,
                    "AVL property violated at '" + n.line + "': child heights " + h0 + " and " + h1 + ctx);
        }
        for (Node k : n.kids) {
            checkNode(k, ctx, checkAvl);
        }
    }

    private void checkStructure(String label, Collection<? extends Shape> expected) {
        checkStructure(label, expected, true);
    }

    private void checkStructure(String label, Collection<? extends Shape> expected, boolean checkAvl) {
        String text = bvh.toString();
        String ctx = "\n[" + label + "] toString was:\n" + text;
        assertNotNull(text, label + ": toString returned null");
        if (expected.isEmpty()) {
            return;
        }
        Node root = parse(text, ctx);
        assertNotNull(root, "toString printed no nodes" + ctx);
        assertEquals(expectedKeys(expected), leafKeysOf(root), "leaves printed by toString != shapes in the BVH" + ctx);
        checkNode(root, ctx, checkAvl);
    }

    // ----- brute-force oracles -----
    private static double cross(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static int classify(int[] c, double px, double py) {
        double[] d = { cross(c[0], c[1], c[2], c[3], px, py), cross(c[2], c[3], c[4], c[5], px, py),
                cross(c[4], c[5], c[0], c[1], px, py) };
        double eps = 1e-7;
        boolean neg = false, pos = false, zero = false;
        for (double x : d) {
            if (x < -eps) {
                neg = true;
            } else if (x > eps) {
                pos = true;
            } else {
                zero = true;
            }
        }
        if (neg && pos) {
            return 0;
        }
        return zero ? -1 : 1;
    }

    private Object bruteRay(List<Shape> live, double y0) {
        Shape best = null;
        double bestX = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        for (Shape s : live) {
            int[] c = coords.get(s);
            double ymin = Math.min(c[1], Math.min(c[3], c[5])), ymax = Math.max(c[1], Math.max(c[3], c[5]));
            if (!(ymin < y0 && y0 < ymax)) {
                continue;
            }
            double entry = Double.POSITIVE_INFINITY;
            for (int k = 0; k < 3; k++) {
                int x1 = c[2 * k], y1 = c[2 * k + 1], x2 = c[(2 * k + 2) % 6], y2 = c[(2 * k + 3) % 6];
                if ((y1 < y0) != (y2 < y0)) {
                    entry = Math.min(entry, x1 + (y0 - y1) * (x2 - x1) / (double) (y2 - y1));
                }
            }
            if (entry < bestX) {
                second = bestX;
                bestX = entry;
                best = s;
            } else if (entry < second) {
                second = entry;
            }
        }
        if (second - bestX < 1e-6) {
            return "TIE";
        }
        return best;
    }

    private void assertMatchesBruteForce(String label, List<Shape> live, Random r) {
        for (int q = 0; q < 200; q++) {
            double px = r.nextDouble() * 320 - 10, py = r.nextDouble() * 320 - 10;
            Set<Shape> expected = new HashSet<>();
            boolean ambiguous = false;
            for (Shape s : live) {
                int cl = classify(coords.get(s), px, py);
                if (cl < 0) {
                    ambiguous = true;
                    break;
                }
                if (cl == 1) {
                    expected.add(s);
                }
            }
            if (ambiguous) {
                continue;
            }
            assertEquals(expected, bvh.findCollision(p(px, py)),
                    "[" + label + "] findCollision(" + px + ", " + py + ") differs from brute force");
        }
        for (int q = 0; q < 60; q++) {
            double y0 = 2 * r.nextInt(160) + 1; // odd integer height: valid integer origin, never on a vertex row
            Object expected = bruteRay(live, y0);
            if ("TIE".equals(expected)) {
                continue;
            }
            assertEquals(expected, bvh.intersectRay(p(-1000, y0), v(1, 0)),
                    "[" + label + "] intersectRay from (-1000, " + y0 + ") direction (1,0) differs from brute force");
        }
    }

    @Test
    @DisplayName("setSplitMethod: SPLIT_MEDIAN ok")
    void setSplit_median_ok() {
        assertDoesNotThrow(() -> bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN));
    }

    @Test
    @DisplayName("setSplitMethod: SPLIT_MIDRANGE ok")
    void setSplit_midrange_ok() {
        assertDoesNotThrow(() -> bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE));
    }

    @Test
    @DisplayName("setSplitMethod toggling still yields correct queries")
    void setSplit_toggle_and_query() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN);
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
    }

    @Test
    @DisplayName("buildBVH: empty list -> queries miss")
    void build_empty() {
        bvh.buildBVH(Collections.emptyList());
        assertTrue(bvh.findCollision(p(0, 0)).isEmpty());
        assertNull(bvh.intersectRay(p(-100, 0), v(1, 0)));
    }

    @Test
    @DisplayName("buildBVH: valid list -> basic hit/miss")
    void build_valid_basic() {
        Triangle a = tri(0, 0, 20, 0, 0, 20);
        Triangle b = tri(100, 100, 120, 100, 100, 120);
        bvh.buildBVH(L(a, b));
        assertTrue(bvh.findCollision(p(5, 5)).contains(a));
        assertTrue(bvh.findCollision(p(110, 110)).contains(b));
        assertTrue(bvh.findCollision(p(50, 50)).isEmpty());
    }

    @Test
    @DisplayName("buildBVH: order invariance of basic queries")
    void build_order_invariance() {
        Triangle a = tri(0, 0, 20, 0, 0, 20);
        Triangle b = tri(100, 0, 120, 0, 110, 20);
        bvh.buildBVH(L(a, b));
        boolean before = !bvh.findCollision(p(5, 5)).isEmpty() && !bvh.findCollision(p(110, 10)).isEmpty();
        bvh.buildBVH(L(b, a));
        boolean after = !bvh.findCollision(p(5, 5)).isEmpty() && !bvh.findCollision(p(110, 10)).isEmpty();
        assertTrue(before && after);
    }

    @Test
    @DisplayName("buildBVH: huge coordinates work")
    void build_huge_coords() {
        Triangle t = tri(1_000_000, 1_000_000, 1_000_100, 1_000_000, 1_000_000, 1_000_100);
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1_000_050, 1_000_050)).isEmpty());
    }

    @Test
    @DisplayName("rebuild replaces contents")
    void rebuild_replaces() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(a));
        Triangle b = tri(100, 100, 120, 100, 100, 120);
        bvh.buildBVH(L(b));
        assertTrue(bvh.findCollision(p(5, 5)).isEmpty());
        assertTrue(bvh.findCollision(p(105, 105)).contains(b));
    }

    // ---------- insert / remove (queries only) ----------
    @Test
    @DisplayName("insert into empty -> becomes hittable")
    void insert_into_empty() {
        bvh.buildBVH(Collections.emptyList());
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.insert(t);
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
    }

    @Test
    @DisplayName("insert after remove of same object is allowed")
    void insert_after_remove_same_ok() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(t));
        bvh.remove(t);
        assertDoesNotThrow(() -> bvh.insert(t));
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
    }

    @Test
    @DisplayName("insert multiple grows coverage across space")
    void insert_multiple_grows_space() {
        bvh.buildBVH(Collections.emptyList());
        Triangle a = tri(-100, -100, -90, -100, -100, -90);
        Triangle b = tri(100, 100, 120, 100, 100, 120);
        bvh.insert(a);
        assertFalse(bvh.findCollision(p(-95, -95)).isEmpty());
        bvh.insert(b);
        assertFalse(bvh.findCollision(p(105, 105)).isEmpty());
    }

    @Test
    @DisplayName("insert then remove updates queries")
    void insert_then_remove_updates() {
        bvh.buildBVH(Collections.emptyList());
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.insert(t);
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
        bvh.remove(t);
        assertFalse(bvh.findCollision(p(1, 1)).contains(t));
    }

    @Test
    @DisplayName("Re-insert shape after rebuild allowed")
    void reinsertion_after_rebuild() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(t));
        bvh.buildBVH(Collections.emptyList());
        assertDoesNotThrow(() -> bvh.insert(t));
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
    }

    @Test
    @DisplayName("remove existing -> region no longer hittable")
    void remove_existing() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(a));
        assertFalse(bvh.findCollision(p(2, 2)).isEmpty());
        bvh.remove(a);
        assertTrue(bvh.findCollision(p(2, 2)).isEmpty());
    }

    @Test
    @DisplayName("remove all -> queries miss")
    void remove_all() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        Triangle b = tri(50, 50, 80, 50, 50, 80);
        bvh.buildBVH(L(a, b));
        bvh.remove(a);
        bvh.remove(b);
        assertTrue(bvh.findCollision(p(5, 5)).isEmpty());
        assertTrue(bvh.findCollision(p(60, 60)).isEmpty());
        assertNull(bvh.intersectRay(p(-100, 0), v(1, 0)));
    }

    // ---------- findCollision ----------
    @Test
    @DisplayName("findCollision: strictly inside and outside")
    void findCollision_inside_outside() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(t));
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
        assertTrue(bvh.findCollision(p(20, 20)).isEmpty());
        assertTrue(bvh.findCollision(p(6, 6)).isEmpty()); // beyond the hypotenuse, inside the bounding box
    }

    @Test
    @DisplayName("findCollision: overlapping triangles return both")
    void findCollision_overlapping_both() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        Triangle b = tri(2, 2, 12, 2, 2, 12);
        bvh.buildBVH(L(a, b));
        Set<Shape> hits = bvh.findCollision(p(3, 3));
        assertTrue(hits.contains(a) && hits.contains(b));
    }

    @Test
    @DisplayName("findCollision: disjoint triangles return correct single sets")
    void findCollision_disjoint() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        Triangle b = tri(100, 100, 120, 100, 100, 120);
        bvh.buildBVH(L(a, b));
        assertEquals(Set.of(a), bvh.findCollision(p(2, 2)));
        assertEquals(Set.of(b), bvh.findCollision(p(105, 105)));
    }

    @Test
    @DisplayName("findCollision: skinny triangle still detected")
    void findCollision_skinny() {
        Triangle skinny = tri(0, 0, 100, 1, 0, 2);
        bvh.buildBVH(L(skinny));
        assertFalse(bvh.findCollision(p(10, 1)).isEmpty());
    }

    // ---------- intersectRay ----------
    @Test
    @DisplayName("intersectRay: straight hit")
    void ray_hit_straight() {
        Triangle t = tri(5, -5, 10, 0, 5, 5);
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(0, 0), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: miss")
    void ray_miss() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(t));
        assertNull(bvh.intersectRay(p(100, 100), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: ray pointing away from the triangle misses")
    void ray_pointing_away() {
        Triangle t = tri(5, -5, 10, 0, 5, 5);
        bvh.buildBVH(L(t));
        assertNull(bvh.intersectRay(p(0, 0), v(-1, 0)));
    }

    @Test
    @DisplayName("intersectRay: choose closest among multiple")
    void ray_closest_among_multiple() {
        Triangle near = tri(5, -5, 10, 0, 5, 5);
        Triangle far = tri(50, -10, 60, 0, 50, 10);
        bvh.buildBVH(L(far, near));
        assertEquals(near, bvh.intersectRay(p(0, 0), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: opposite direction hit possible")
    void ray_opposite_direction_can_hit() {
        Triangle t = tri(-5, -5, 5, -5, 0, 5);
        bvh.buildBVH(L(t));
        assertNotNull(bvh.intersectRay(p(20, 0), v(-1, 0)));
    }

    @Test
    @DisplayName("intersectRay: vertical ray works")
    void ray_vertical() {
        Triangle t = tri(0, 0, 10, 0, 5, 10);
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(5, -10), v(0, 1)));
    }

    @Test
    @DisplayName("intersectRay: horizontal ray works")
    void ray_horizontal() {
        Triangle t = tri(0, 0, 0, 10, 10, 0);
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(-10, 1), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: diagonal ray works")
    void ray_diagonal() {
        Triangle t = tri(10, 10, 20, 10, 10, 20);
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(0, 0), v(1, 1)));
    }

    // ---------- stress / robustness of results ----------
    @Test
    @DisplayName("Stress: 1000 tiny triangles, targeted rays")
    void stress_many_tiny() {
        List<Shape> tris = new ArrayList<>();
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 100; col++) {
                int bx = col * 5, by = row * 10;
                tris.add(tri(bx, by, bx + 2, by, bx + 1, by + 2));
            }
        }
        bvh.buildBVH(tris);
        assertNotNull(bvh.intersectRay(p(-5, 1), v(1, 0)));
        assertNull(bvh.intersectRay(p(-5, 5), v(1, 0)));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
    }

    @Test
    @DisplayName("Extreme negatives & tiny triangles")
    void extremes_negative_tiny() {
        Triangle tiny = tri(-1, -1, 0, -1, -1, 0);
        bvh.buildBVH(L(tiny));
        assertFalse(bvh.findCollision(p(-0.75, -0.75)).isEmpty());
        assertEquals(tiny, bvh.intersectRay(p(-5, -0.5), v(1, 0)));
    }

    @Test
    @DisplayName("Midrange split handles difficult skew")
    void midrange_split_skew() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        Triangle a = tri(0, 0, 1, 0, 0, 1);
        Triangle b = tri(1000, 0, 1001, 0, 1000, 1);
        Triangle c = tri(2000, 0, 2001, 0, 2000, 1);
        bvh.buildBVH(L(a, b, c));
        assertFalse(bvh.findCollision(p(0.2, 0.2)).isEmpty());
        assertFalse(bvh.findCollision(p(1000.2, 0.2)).isEmpty());
        assertFalse(bvh.findCollision(p(2000.2, 0.2)).isEmpty());
    }

    @Test
    @DisplayName("Median split handles odd/even counts without throwing")
    void median_split_even_odd() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN);
        List<Shape> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            list.add(tri(i * 10, 0, i * 10 + 5, 0, i * 10, 5));
        }
        assertDoesNotThrow(() -> bvh.buildBVH(list));
        list.add(tri(100, 0, 105, 0, 100, 5));
        assertDoesNotThrow(() -> bvh.buildBVH(list));
    }

    // ---------- differential tests against brute force ----------
    @Test
    @DisplayName("findCollision/intersectRay match brute force after build (both split methods)")
    void oracle_after_build() {
        for (BVH.SplitMethod m : BVH.SplitMethod.values()) {
            bvh = create();
            bvh.setSplitMethod(m);
            Random r = new Random(11);
            List<Shape> live = shapes(0, 80, r);
            bvh.buildBVH(new ArrayList<>(live));
            assertMatchesBruteForce("build " + m, live, r);
        }
    }

    // ---------- toString: format and structure spec section 2.1 ----------
    @Test
    @DisplayName("toString is non-null and non-empty when non-empty BVH")
    void toString_nonEmpty() {
        bvh.buildBVH(L(tri(0, 0, 10, 0, 0, 10)));
        String s = bvh.toString();
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    @Test
    @DisplayName("toString: single triangle prints exactly one leaf line")
    void toString_single_leaf() {
        Triangle t = tri(0, 0, 10, 0, 0, 10);
        bvh.buildBVH(L(t));
        checkStructure("single triangle", L(t));
        assertEquals(1, parse(bvh.toString(), "").leaves());
    }

    @Test
    @DisplayName("toString: spec Figure 2 example (4 triangles) gives the pictured tree")
    void toString_figure2_example() {
        // NOTE: the last triangle uses (5,3) as in the spec's toString example; the
        // figure's text shows (5,4).
        for (BVH.SplitMethod m : BVH.SplitMethod.values()) {
            bvh = create();
            bvh.setSplitMethod(m);
            Triangle t1 = tri(0, 0, 1, 0, 1, 2);
            Triangle t2 = tri(1, 5, 2, 3, 3, 5);
            Triangle t3 = tri(2, 2, 3, 1, 4, 2);
            Triangle t4 = tri(4, 5, 5, 3, 5, 5);
            bvh.buildBVH(L(t4, t2, t1, t3));
            String ctx = "\n[figure 2, " + m + "] toString was:\n" + bvh.toString();
            checkStructure("figure 2 " + m, L(t1, t2, t3, t4));
            Node root = parse(bvh.toString(), ctx);
            assertArrayEquals(new double[] { 0, 0, 5, 5 }, root.box(), 1e-9, "root box" + ctx);
            assertEquals(2, root.kids.size(), ctx);
            List<Node> kids = new ArrayList<>(root.kids);
            kids.sort(Comparator.comparingDouble(n -> n.box()[0]));
            assertArrayEquals(new double[] { 0, 0, 3, 5 }, kids.get(0).box(), 1e-9, "left subtree box" + ctx);
            assertArrayEquals(new double[] { 2, 1, 5, 5 }, kids.get(1).box(), 1e-9, "right subtree box" + ctx);
            assertEquals(expectedKeys(L(t1, t2)), leafKeysOf(kids.get(0)), "left subtree leaves" + ctx);
            assertEquals(expectedKeys(L(t3, t4)), leafKeysOf(kids.get(1)), "right subtree leaves" + ctx);
        }
    }

    @Test
    @DisplayName("structure after buildBVH: format, leaves, tight boxes (many sizes, both splits); AVL for median")
    void structure_after_build() {
        for (BVH.SplitMethod m : BVH.SplitMethod.values()) {
            for (int n : new int[] { 1, 2, 3, 4, 5, 7, 8, 13, 32, 50, 100 }) {
                bvh = create();
                bvh.setSplitMethod(m);
                List<Shape> s = shapes(0, n, new Random(n));
                bvh.buildBVH(new ArrayList<>(s));
                // a midrange split may legitimately be lopsided, so balance is only required
                // for the median build
                checkStructure("build n=" + n + " " + m, s, m == BVH.SplitMethod.SPLIT_MEDIAN);
            }
        }
    }

    @Test
    @DisplayName("structure after buildBVH with no split method set (default median)")
    void structure_after_build_default_split() {
        List<Shape> s = shapes(0, 25, new Random(5));
        bvh.buildBVH(new ArrayList<>(s));
        checkStructure("default split", s);
    }

    @Test
    @DisplayName("SPLIT_MEDIAN: root split is balanced (leaf counts differ by at most 1)")
    void median_split_is_balanced() {
        for (int n : new int[] { 4, 7, 8, 9, 20 }) {
            bvh = create();
            bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN);
            List<Shape> s = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                s.add(tri(i * 50, i * 50, i * 50 + 10, i * 50, i * 50, i * 50 + 10));
            }
            bvh.buildBVH(s);
            String ctx = "\n[median n=" + n + "] toString was:\n" + bvh.toString();
            Node root = parse(bvh.toString(), ctx);
            int a = root.kids.get(0).leaves(), b = root.kids.get(1).leaves();
            assertTrue(Math.abs(a - b) <= 1, "median split should be balanced but root children hold " + a + " and "
                    + b + " triangles" + ctx);
        }
    }

    @Test
    @DisplayName("SPLIT_MIDRANGE: one far-away triangle is isolated from a tight cluster at the root")
    void midrange_split_isolates_outlier() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        List<Shape> s = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            s.add(tri(i * 5, i * 5, i * 5 + 4, i * 5, i * 5, i * 5 + 4));
        }
        s.add(tri(1000, 1000, 1004, 1000, 1000, 1004));
        bvh.buildBVH(s);
        String ctx = "\n[midrange outlier] toString was:\n" + bvh.toString();
        Node root = parse(bvh.toString(), ctx);
        List<Integer> counts = new ArrayList<>(
                Arrays.asList(root.kids.get(0).leaves(), root.kids.get(1).leaves()));
        Collections.sort(counts);
        assertEquals(Arrays.asList(1, 5), counts,
                "midrange split of 5 clustered + 1 distant triangle should give subtrees of 1 and 5 leaves" + ctx);
    }

    // ---------- insert: priorities and balance (spec section 2.1) ----------
    @Test
    @DisplayName("insert priority 1: a leaf child is preferred over an internal child")
    void insert_prefers_leaf_child() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        Triangle x = tri(0, 0, 4, 0, 0, 4);
        Triangle y = tri(6, 6, 10, 6, 6, 10);
        Triangle z = tri(1000, 1000, 1004, 1000, 1000, 1004);
        bvh.buildBVH(L(x, y, z));
        Node before = parse(bvh.toString(), "");
        assertEquals(2, before.kids.size(), "precondition: unexpected build result\n" + bvh.toString());
        assertTrue(before.kids.get(0).leaf != before.kids.get(1).leaf,
                "precondition: expected root children = one leaf + one internal node, got:\n" + bvh.toString());
        // new triangle sits inside the cluster, so the internal child's box would not
        // grow at all
        Triangle d = tri(2, 2, 5, 2, 2, 5);
        bvh.insert(d);
        String ctx = "\n[insert priority 1] toString was:\n" + bvh.toString();
        checkStructure("insert priority 1", L(x, y, z, d));
        Node root = parse(bvh.toString(), ctx);
        boolean ok = false;
        for (Node k : root.kids) {
            if (leafKeysOf(k).equals(expectedKeys(L(z, d)))) {
                ok = true;
            }
        }
        assertTrue(ok, "spec priority 1: children that are leaves get higher priority, so the new triangle should "
                + "end up paired with the lone leaf child" + ctx);
    }

    @Test
    @DisplayName("insert priority 2: with equal leaf status, the child whose box grows less is chosen")
    void insert_prefers_smaller_area_growth() {
        Triangle a = tri(0, 0, 10, 0, 0, 10);
        Triangle b = tri(1000, 1000, 1010, 1000, 1000, 1010);
        bvh.buildBVH(L(a, b));
        Triangle c = tri(12, 12, 20, 12, 12, 20); // right next to a
        bvh.insert(c);
        String ctx = "\n[insert priority 2] toString was:\n" + bvh.toString();
        checkStructure("insert priority 2", L(a, b, c));
        Node root = parse(bvh.toString(), ctx);
        boolean ok = false;
        for (Node k : root.kids) {
            if (leafKeysOf(k).equals(expectedKeys(L(a, c)))) {
                ok = true;
            }
        }
        assertTrue(ok, "new triangle should be grouped with the nearby triangle (smaller bounding-box growth)" + ctx);
    }

    @Test
    @DisplayName("structure after many inserts onto a built tree (format, leaves, boxes, AVL)")
    void structure_after_inserts_onto_built_tree() {
        Random r = new Random(21);
        List<Shape> live = shapes(0, 8, r);
        bvh.buildBVH(new ArrayList<>(live));
        for (int i = 8; i < 60; i++) {
            Shape s = randTri(i, r);
            bvh.insert(s);
            live.add(s);
            checkStructure("after insert #" + (i - 7), live);
        }
        assertMatchesBruteForce("after inserts", live, r);
    }

    @Test
    @DisplayName("structure after inserting everything into an empty BVH (format, leaves, boxes, AVL)")
    void structure_after_inserts_from_empty() {
        Random r = new Random(22);
        List<Shape> live = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Shape s = randTri(i, r);
            bvh.insert(s);
            live.add(s);
            checkStructure("empty + insert #" + (i + 1), live);
        }
        assertMatchesBruteForce("after inserts from empty", live, r);
    }

    @Test
    @DisplayName("structure after many removes (format, leaves, boxes, AVL)")
    void structure_after_removes() {
        Random r = new Random(23);
        List<Shape> live = shapes(0, 50, r);
        bvh.buildBVH(new ArrayList<>(live));
        List<Shape> order = new ArrayList<>(live);
        Collections.shuffle(order, new Random(99));
        int step = 0;
        for (Shape s : order) {
            bvh.remove(s);
            live.remove(s);
            step++;
            checkStructure("after remove #" + step, live);
            if (step % 10 == 0) {
                assertMatchesBruteForce("after remove #" + step, live, r);
            }
        }
    }

    @Test
    @DisplayName("random mix of 150 inserts/removes keeps all invariants and matches brute force")
    void mixed_operations_keep_invariants() {
        Random r = new Random(7);
        List<Shape> live = new ArrayList<>(shapes(0, 10, r));
        bvh.buildBVH(new ArrayList<>(live));
        int next = 10;
        for (int step = 0; step < 150; step++) {
            boolean insert = live.isEmpty() || (next < 200 && r.nextInt(100) < 55);
            if (insert) {
                Shape s = randTri(next++, r);
                bvh.insert(s);
                live.add(s);
            } else {
                Shape s = live.remove(r.nextInt(live.size()));
                bvh.remove(s);
            }
            checkStructure("mixed step " + step + (insert ? " (insert)" : " (remove)"), live);
            if (step % 10 == 0) {
                assertMatchesBruteForce("mixed step " + step, live, r);
            }
        }
    }

    @Nested
    @Tag("boundary")
    class UnspecifiedBoundaryBehavior {

        @Test
        @DisplayName("[unspecified] findCollision: points on edge / vertex count as inside")
        void findCollision_edge_and_vertex() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(t));
            assertTrue(bvh.findCollision(p(5, 5)).contains(t)); // on hypotenuse
            assertTrue(bvh.findCollision(p(0, 0)).contains(t)); // vertex
        }

        @Test
        @DisplayName("[unspecified] intersectRay: starting inside returns that triangle")
        void ray_start_inside() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(t));
            assertEquals(t, bvh.intersectRay(p(1, 1), v(1, 0)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay: grazing along an edge counts as a hit")
        void ray_grazing_edge() {
            Triangle t = tri(0, 10, 10, 0, 0, 0);
            bvh.buildBVH(L(t));
            assertEquals(t, bvh.intersectRay(p(-5, 15), v(1, -1)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay: ray starting at a vertex hits")
        void ray_from_vertex() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(t));
            assertEquals(t, bvh.intersectRay(p(0, 0), v(1, 1)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay: ray along the bottom edge of a flat triangle hits")
        void colinear_edge_aligned() {
            Triangle flat = tri(0, 0, 10, 0, 5, 1);
            bvh.buildBVH(L(flat));
            assertFalse(bvh.findCollision(p(5, 0)).isEmpty());
            assertEquals(flat, bvh.intersectRay(p(-5, 0), v(1, 0)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay: a fractional origin is respected (spec's origin is an integer Point)")
        void ray_fractional_origin() {
            Triangle t = tri(10, 25, 20, 25, 10, 35);
            bvh.buildBVH(L(t));
            // y = 24.6 is below the triangle (min y = 25): must miss. An implementation
            // that rounds the origin to 25 would hit.
            assertNull(bvh.intersectRay(p(0, 24.6), v(1, 0)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay: zero-length direction throws IllegalArgumentException")
        void ray_zero_length_dir_guard() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(t));
            assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(p(0, 0), new Vector2D(0, 0)));
        }

        @Test
        @DisplayName("[unspecified] remove of non-existent shape is a no-op")
        void remove_nonexistent_ok() {
            Triangle a = tri(0, 0, 10, 0, 0, 10);
            Triangle ghost = tri(99, 99, 100, 99, 99, 100);
            bvh.buildBVH(L(a));
            assertDoesNotThrow(() -> bvh.remove(ghost));
            assertTrue(bvh.findCollision(p(1, 1)).contains(a));
        }

        @Test
        @DisplayName("[unspecified] remove twice is idempotent")
        void remove_twice_ok() {
            Triangle a = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(a));
            bvh.remove(a);
            assertDoesNotThrow(() -> bvh.remove(a));
            assertTrue(bvh.findCollision(p(1, 1)).isEmpty());
        }

        @Test
        @DisplayName("[unspecified] toString of an empty BVH does not return null")
        void toString_empty_ok() {
            bvh.buildBVH(Collections.emptyList());
            assertNotNull(bvh.toString());
        }

        @Test
        @DisplayName("[unspecified] buildBVH order invariance of triangle collisions at vertices")
        void order_invariance_vertices() {
            Triangle a = tri(0, 0, 20, 0, 0, 20);
            Triangle b = tri(100, 0, 120, 0, 110, 20);
            bvh.buildBVH(L(a, b));
            assertFalse(bvh.findCollision(p(0, 0)).isEmpty());
            bvh.buildBVH(L(b, a));
            assertFalse(bvh.findCollision(p(0, 0)).isEmpty());
        }
    }

    @Nested
    @Tag("robustness")
    class UnspecifiedInvalidInputHandling {

        @Test
        @DisplayName("[unspecified] setSplitMethod(null) -> IAE")
        void setSplit_null() {
            assertThrows(IllegalArgumentException.class, () -> bvh.setSplitMethod(null));
        }

        @Test
        @DisplayName("[unspecified] buildBVH(null) -> IAE")
        void build_nullList() {
            assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(null));
        }

        @Test
        @DisplayName("[unspecified] buildBVH with a null element -> IAE (not NPE)")
        void build_listContainsNull() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(L(t, null)));
        }

        @Test
        @DisplayName("[unspecified] buildBVH with duplicates -> IAE")
        void build_duplicates() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(L(t, t)));
        }

        @Test
        @DisplayName("[unspecified] insert(null) -> IAE")
        void insert_null() {
            assertThrows(IllegalArgumentException.class, () -> bvh.insert(null));
        }

        @Test
        @DisplayName("[unspecified] insert duplicate -> IAE")
        void insert_duplicate() {
            Triangle t = tri(0, 0, 10, 0, 0, 10);
            bvh.buildBVH(L(t));
            assertThrows(IllegalArgumentException.class, () -> bvh.insert(t));
        }

        @Test
        @DisplayName("[unspecified] remove(null) -> IAE")
        void remove_null() {
            assertThrows(IllegalArgumentException.class, () -> bvh.remove(null));
        }

        @Test
        @DisplayName("[unspecified] findCollision(null) -> IAE")
        void findCollision_null() {
            assertThrows(IllegalArgumentException.class, () -> bvh.findCollision(null));
        }

        @Test
        @DisplayName("[unspecified] intersectRay(null origin) -> IAE")
        void ray_null_origin() {
            assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(null, v(1, 0)));
        }

        @Test
        @DisplayName("[unspecified] intersectRay(null direction) -> IAE")
        void ray_null_dir() {
            assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(p(0, 0), null));
        }
    }
}