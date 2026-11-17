package assignment;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BVHDeepTest {

    private BoundedVolumeHierarchy bvh;

    private static Triangle tri(Point a, Point b, Point c) {
        return new Triangle(a, b, c);
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

    @BeforeEach
    void init() {
        bvh = new BoundedVolumeHierarchy();
    }

    @Test
    @DisplayName("setSplitMethod: null -> IAE")
    void setSplit_null() {
        assertThrows(IllegalArgumentException.class, () -> bvh.setSplitMethod(null));
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
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN);
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
    }

    @Test
    @DisplayName("buildBVH: null list -> IAE")
    void build_nullList() {
        assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(null));
    }

    @Test
    @DisplayName("buildBVH: contains null element -> IAE")
    void build_listContainsNull() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(L(t, null)));
    }

    @Test
    @DisplayName("buildBVH: duplicates -> IAE")
    void build_duplicates() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        assertThrows(IllegalArgumentException.class, () -> bvh.buildBVH(L(t, t)));
    }

    @Test
    @DisplayName("buildBVH: empty list -> queries miss")
    void build_empty() {
        bvh.buildBVH(Collections.emptyList());
        assertTrue(bvh.getAllShapes().isEmpty());
        assertTrue(bvh.findCollision(p(0, 0)).isEmpty());
        assertNull(bvh.intersectRay(p(-100, 0), v(1, 0)));
    }

    @Test
    @DisplayName("buildBVH: valid list -> basic hit/miss")
    void build_valid_basic() {
        Triangle a = tri(new Point(0, 0), new Point(20, 0), new Point(0, 20));
        Triangle b = tri(new Point(100, 100), new Point(120, 100), new Point(100, 120));
        bvh.buildBVH(L(a, b));
        assertTrue(bvh.findCollision(p(5, 5)).contains(a));
        assertTrue(bvh.findCollision(p(110, 110)).contains(b));
        assertTrue(bvh.findCollision(p(50, 50)).isEmpty());
    }

    @Test
    @DisplayName("buildBVH: order invariance of basic queries")
    void build_order_invariance() {
        Triangle a = tri(new Point(0, 0), new Point(20, 0), new Point(0, 20));
        Triangle b = tri(new Point(100, 0), new Point(120, 0), new Point(110, 20));
        bvh.buildBVH(L(a, b));
        boolean before = !bvh.findCollision(p(5, 5)).isEmpty() && !bvh.findCollision(p(110, 10)).isEmpty();
        bvh.buildBVH(L(b, a));
        boolean after = !bvh.findCollision(p(5, 5)).isEmpty() && !bvh.findCollision(p(110, 10)).isEmpty();
        assertTrue(before && after);
    }

    @Test
    @DisplayName("buildBVH: huge coordinates work")
    void build_huge_coords() {
        Triangle t = tri(new Point(1_000_000, 1_000_000), new Point(1_000_100, 1_000_000),
                new Point(1_000_000, 1_000_100));
        bvh.buildBVH(L(t));
        assertFalse(bvh.findCollision(p(1_000_050, 1_000_050)).isEmpty());
    }

    @Test
    @DisplayName("rebuild replaces contents")
    void rebuild_replaces() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(a));
        Triangle b = tri(new Point(100, 100), new Point(120, 100), new Point(100, 120));
        bvh.buildBVH(L(b));
        assertTrue(bvh.findCollision(p(5, 5)).isEmpty());
        assertFalse(bvh.findCollision(p(105, 105)).isEmpty());
        assertFalse(bvh.getAllShapes().contains(a));
        assertTrue(bvh.getAllShapes().contains(b));
    }

    @Test
    @DisplayName("getAllShapes returns a copy, not a live view")
    void getAllShapes_copy() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(a));
        List<Shape> copy = bvh.getAllShapes();
        copy.clear();
        assertFalse(bvh.getAllShapes().isEmpty());
    }

    @Test
    @DisplayName("toString is non-null and non-empty when non-empty BVH")
    void toString_nonEmpty() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(a));
        String s = bvh.toString();
        assertNotNull(s);
        assertFalse(s.isEmpty());
    }

    @Test
    @DisplayName("toString is not null for empty BVH")
    void toString_empty_ok() {
        bvh.buildBVH(Collections.emptyList());
        assertNotNull(bvh.toString());
    }

    @Test
    @DisplayName("insert null -> IAE")
    void insert_null() {
        assertThrows(IllegalArgumentException.class, () -> bvh.insert(null));
    }

    @Test
    @DisplayName("insert into empty -> becomes hittable")
    void insert_into_empty() {
        bvh.buildBVH(Collections.emptyList());
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.insert(t);
        assertTrue(bvh.getAllShapes().contains(t));
        assertFalse(bvh.findCollision(p(1, 1)).isEmpty());
    }

    @Test
    @DisplayName("insert duplicate -> IAE")
    void insert_duplicate() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        assertThrows(IllegalArgumentException.class, () -> bvh.insert(t));
    }

    @Test
    @DisplayName("insert after remove of same object is allowed")
    void insert_after_remove_same_ok() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        bvh.remove(t);
        assertDoesNotThrow(() -> bvh.insert(t));
        assertTrue(bvh.getAllShapes().contains(t));
    }

    @Test
    @DisplayName("insert multiple grows coverage across space")
    void insert_multiple_grows_space() {
        bvh.buildBVH(Collections.emptyList());
        Triangle a = tri(new Point(-100, -100), new Point(-90, -100), new Point(-100, -90));
        Triangle b = tri(new Point(100, 100), new Point(120, 100), new Point(100, 120));
        bvh.insert(a);
        assertFalse(bvh.findCollision(p(-95, -95)).isEmpty());
        bvh.insert(b);
        assertFalse(bvh.findCollision(p(105, 105)).isEmpty());
    }

    @Test
    @DisplayName("remove null -> IAE")
    void remove_null() {
        assertThrows(IllegalArgumentException.class, () -> bvh.remove(null));
    }

    @Test
    @DisplayName("remove of non-existent shape is no-op")
    void remove_nonexistent_ok() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        Triangle ghost = tri(new Point(99, 99), new Point(100, 99), new Point(99, 100));
        bvh.buildBVH(L(a));
        assertDoesNotThrow(() -> bvh.remove(ghost));
        assertTrue(bvh.getAllShapes().contains(a));
    }

    @Test
    @DisplayName("remove existing -> region no longer hittable")
    void remove_existing() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(a));
        assertFalse(bvh.findCollision(p(2, 2)).isEmpty());
        bvh.remove(a);
        assertTrue(bvh.findCollision(p(2, 2)).isEmpty());
        assertFalse(bvh.getAllShapes().contains(a));
    }

    @Test
    @DisplayName("remove all -> BVH becomes empty and queries miss")
    void remove_all() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        Triangle b = tri(new Point(50, 50), new Point(80, 50), new Point(50, 80));
        bvh.buildBVH(L(a, b));
        bvh.remove(a);
        bvh.remove(b);
        assertTrue(bvh.getAllShapes().isEmpty());
        assertTrue(bvh.findCollision(p(5, 5)).isEmpty());
        assertNull(bvh.intersectRay(p(-100, 0), v(1, 0)));
    }

    @Test
    @DisplayName("remove twice is idempotent")
    void remove_twice_ok() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(a));
        bvh.remove(a);
        assertDoesNotThrow(() -> bvh.remove(a));
        assertTrue(bvh.getAllShapes().isEmpty());
    }

    @Test
    @DisplayName("findCollision: null -> IAE")
    void findCollision_null() {
        assertThrows(IllegalArgumentException.class, () -> bvh.findCollision(null));
    }

    @Test
    @DisplayName("findCollision: inside, outside, edge, vertex")
    void findCollision_various_positions() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        assertTrue(bvh.findCollision(p(1, 1)).contains(t));
        assertTrue(bvh.findCollision(p(5, 5)).contains(t));
        assertTrue(bvh.findCollision(p(0, 0)).contains(t));
        assertTrue(bvh.findCollision(p(20, 20)).isEmpty());
    }

    @Test
    @DisplayName("findCollision: overlapping triangles return both")
    void findCollision_overlapping_both() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        Triangle b = tri(new Point(2, 2), new Point(12, 2), new Point(2, 12));
        bvh.buildBVH(L(a, b));
        Set<Shape> hits = bvh.findCollision(p(3, 3));
        assertTrue(hits.contains(a) && hits.contains(b));
    }

    @Test
    @DisplayName("findCollision: disjoint triangles return correct single sets")
    void findCollision_disjoint() {
        Triangle a = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        Triangle b = tri(new Point(100, 100), new Point(120, 100), new Point(100, 120));
        bvh.buildBVH(L(a, b));
        assertEquals(Set.of(a), bvh.findCollision(p(2, 2)));
        assertEquals(Set.of(b), bvh.findCollision(p(105, 105)));
    }

    @Test
    @DisplayName("findCollision: skinny triangle still detected")
    void findCollision_skinny() {
        Triangle skinny = tri(new Point(0, 0), new Point(100, 1), new Point(0, 2));
        bvh.buildBVH(L(skinny));
        assertFalse(bvh.findCollision(p(10, 1)).isEmpty());
    }

    @Test
    @DisplayName("intersectRay: null origin -> IAE")
    void ray_null_origin() {
        assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(null, v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: null dir -> IAE")
    void ray_null_dir() {
        assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(p(0, 0), null));
    }

    @Test
    @DisplayName("intersectRay: straight hit")
    void ray_hit_straight() {
        Triangle t = tri(new Point(5, -5), new Point(10, 0), new Point(5, 5));
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(0, 0), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: miss")
    void ray_miss() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        assertNull(bvh.intersectRay(p(100, 100), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: starting inside returns a hit")
    void ray_start_inside() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(1, 1), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: choose closest among multiple")
    void ray_closest_among_multiple() {
        Triangle near = tri(new Point(5, -5), new Point(10, 0), new Point(5, 5));
        Triangle far = tri(new Point(50, -10), new Point(60, 0), new Point(50, 10));
        bvh.buildBVH(L(far, near));
        assertEquals(near, bvh.intersectRay(p(0, 0), v(1, 0)));
    }

    @Test
    @DisplayName("intersectRay: opposite direction hit possible")
    void ray_opposite_direction_can_hit() {
        Triangle t = tri(new Point(-5, -5), new Point(5, -5), new Point(0, 5));
        bvh.buildBVH(L(t));
        assertNotNull(bvh.intersectRay(p(0, 0), v(-1, 0)));
    }

    @Test
    @DisplayName("intersectRay: grazing along edge counts as hit")
    void ray_grazing_edge() {

        Triangle t = tri(new Point(0, 10), new Point(10, 0), new Point(0, 0));
        bvh.buildBVH(L(t));

        assertEquals(t, bvh.intersectRay(p(-5, 15), v(1, -1)));
    }

    @Test
    @DisplayName("intersectRay: vertical ray works")
    void ray_vertical() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(5, 10));
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(5, -10), v(0, 1)));
    }

    @Test
    @DisplayName("intersectRay: horizontal ray works")
    void ray_horizontal() {
        Triangle t = tri(new Point(0, 0), new Point(0, 10), new Point(10, 0));
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(-10, 1), v(1, 0)));
    }

    @Test
    @DisplayName("Stress: 1000 tiny triangles, targeted rays")
    void stress_many_tiny() {
        List<Shape> tris = new ArrayList<>();
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 100; col++) {
                int bx = col * 5;
                int by = row * 10;
                tris.add(tri(new Point(bx, by), new Point(bx + 2, by), new Point(bx + 1, by + 2)));
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
        Triangle tiny = tri(new Point(-1, -1), new Point(0, -1), new Point(-1, 0));
        bvh.buildBVH(L(tiny));
        assertFalse(bvh.findCollision(p(-1, -1)).isEmpty());
        assertEquals(tiny, bvh.intersectRay(p(-5, -1), v(1, 0)));
    }

    @Test
    @DisplayName("getAllShapes reflects inserts/removes after build")
    void getAllShapes_updates() {
        bvh.buildBVH(Collections.emptyList());
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.insert(t);
        assertTrue(bvh.getAllShapes().contains(t));
        bvh.remove(t);
        assertFalse(bvh.getAllShapes().contains(t));
    }

    @Test
    @DisplayName("Re-insert shape after rebuild allowed")
    void reinsertion_after_rebuild() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        bvh.buildBVH(Collections.emptyList());
        assertDoesNotThrow(() -> bvh.insert(t));
        assertTrue(bvh.getAllShapes().contains(t));
    }

    @Test
    @DisplayName("Midrange split also handles difficult skew")
    void midrange_split_skew() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MIDRANGE);
        Triangle a = tri(new Point(0, 0), new Point(1, 0), new Point(0, 1));
        Triangle b = tri(new Point(1000, 0), new Point(1001, 0), new Point(1000, 1));
        Triangle c = tri(new Point(2000, 0), new Point(2001, 0), new Point(2000, 1));
        bvh.buildBVH(L(a, b, c));
        assertFalse(bvh.findCollision(p(0, 0)).isEmpty());
        assertFalse(bvh.findCollision(p(1000, 0)).isEmpty());
        assertFalse(bvh.findCollision(p(2000, 0)).isEmpty());
    }

    @Test
    @DisplayName("Median split handles odd/even counts without throwing")
    void median_split_even_odd() {
        bvh.setSplitMethod(BVH.SplitMethod.SPLIT_MEDIAN);
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < 7; i++)
            shapes.add(tri(new Point(i * 10, 0), new Point(i * 10 + 5, 0), new Point(i * 10, 5)));
        assertDoesNotThrow(() -> bvh.buildBVH(shapes));
        shapes.add(tri(new Point(100, 0), new Point(105, 0), new Point(100, 5)));
        assertDoesNotThrow(() -> bvh.buildBVH(shapes));
    }

    @Test
    @DisplayName("Edge case: colinear edge-aligned triangle still queryable")
    void colinear_edge_aligned() {

        Triangle flat = tri(new Point(0, 0), new Point(10, 0), new Point(5, 1));
        bvh.buildBVH(L(flat));
        assertFalse(bvh.findCollision(p(5, 0)).isEmpty());
        assertEquals(flat, bvh.intersectRay(p(-5, 0), v(1, 0)));
    }

    @Test
    @DisplayName("Edge case: ray from vertex outward still hits triangle")
    void ray_from_vertex() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));
        assertEquals(t, bvh.intersectRay(p(0, 0), v(1, 1)));
    }

    @Test
    @DisplayName("Edge case: zero-length direction should throw (defensive)")
    void ray_zero_length_dir_guard() {
        Triangle t = tri(new Point(0, 0), new Point(10, 0), new Point(0, 10));
        bvh.buildBVH(L(t));

        assertThrows(IllegalArgumentException.class, () -> bvh.intersectRay(p(0, 0), new Vector2D(0, 0)));
    }
}