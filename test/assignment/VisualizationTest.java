package assignment;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Point;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class VisualizationTest {

    private Visualization viz;
    private BoundedVolumeHierarchy bvh;

    @BeforeEach
    void setup() {
        viz = new Visualization();
        bvh = new BoundedVolumeHierarchy();
    }

    // ---------- helpers ----------
    private static Triangle tri(int x1, int y1, int x2, int y2, int x3, int y3) {
        return new Triangle(new Point(x1, y1), new Point(x2, y2), new Point(x3, y3));
    }

    /**
     * Right triangle like the one in spec Figure 3: hypotenuse from virtual (0,100)
     * to (50,50).
     */
    private static Triangle figure3() {
        return tri(0, 100, 50, 100, 50, 50);
    }

    private void useShapes(Shape... shapes) {
        bvh.buildBVH(new ArrayList<>(Arrays.asList(shapes)));
        viz.setBoundingVolumeHierarchy(bvh);
    }

    private static int pixelsNearLine(List<Point> px, double c, int xlo, int xhi) {
        int n = 0;
        for (Point q : px) {
            if (q.x >= xlo && q.x <= xhi && Math.abs(q.x + q.y - c) <= 1.5) {
                n++;
            }
        }
        return n;
    }

    // =====================================================================
    // moveSquare
    // =====================================================================
    @Test
    @DisplayName("moveSquare: all four keys move the square in empty space")
    void move_free_space() {
        useShapes();
        for (char k : "WASD".toCharArray()) {
            assertTrue(viz.moveSquare(k), "key " + k + " should move the square when nothing is in the way");
        }
    }

    @Test
    @DisplayName("moveSquare: a far-away triangle does not block movement")
    void move_far_triangle_does_not_block() {
        useShapes(tri(5000, 5000, 5100, 5000, 5000, 5100));
        for (char k : "WASD".toCharArray()) {
            assertTrue(viz.moveSquare(k), "key " + k);
        }
    }

    @Test
    @DisplayName("moveSquare: square surrounded by a huge triangle cannot move in any direction")
    void move_blocked_by_enclosing_triangle() {
        useShapes(tri(-5000, -5000, 20000, -5000, -5000, 20000));
        for (char k : "WASD".toCharArray()) {
            assertFalse(viz.moveSquare(k), "key " + k + " would move into a triangle and must be refused");
        }
    }

    @Test
    @DisplayName("moveSquare: pressing D toward a wall eventually stops (and only after a sensible distance)")
    void move_stops_at_wall() {
        // a wall covering x >= 50 around y = 0
        useShapes(tri(50, -2000, 50, 2000, 4000, 0));
        int successes = 0;
        boolean blocked = false;
        for (int i = 0; i < 40; i++) {
            if (viz.moveSquare('D')) {
                successes++;
            } else {
                blocked = true;
                break;
            }
        }
        assertTrue(blocked, "square walked straight through a wall (" + successes + " successful moves)");
        // 5 units per move; a 21x21 square must stop before center passes x=50 and
        // cannot stop absurdly early
        assertTrue(successes >= 5 && successes <= 11,
                "expected roughly 7-10 successful moves before the wall at x=50, got " + successes);
        assertTrue(viz.moveSquare('A'), "moving away from the wall should work");
    }

    @Test
    @DisplayName("moveSquare: a refused move leaves the picture unchanged")
    void refused_move_keeps_picture() {
        useShapes(tri(50, -2000, 50, 2000, 4000, 0), figure3());
        int guard = 0;
        while (viz.moveSquare('D') && guard++ < 40) {
            // walk to the wall
        }
        List<Point> before = new ArrayList<>(viz.drawGUI());
        assertFalse(viz.moveSquare('D'));
        List<Point> after = new ArrayList<>(viz.drawGUI());
        assertEquals(new HashSet<>(before), new HashSet<>(after), "picture changed although the square did not move");
    }

    @Test
    @DisplayName("drawGUI: never returns null")
    void draw_not_null() {
        useShapes();
        assertNotNull(viz.drawGUI());
    }

    @Test
    @DisplayName("drawGUI: every returned pixel lies inside the 400x400 frame")
    void draw_inside_frame() {
        useShapes(figure3(), tri(-80, -80, -40, -80, -80, -40), tri(60, -90, 90, -90, 60, -60));
        for (Point q : viz.drawGUI()) {
            assertTrue(q.x >= 0 && q.x <= 400 && q.y >= 0 && q.y <= 400, "pixel outside the frame: " + q);
        }
    }

    @Test
    @DisplayName("drawGUI: a triangle far outside the frame adds no pixels")
    void draw_far_triangle_adds_nothing() {
        useShapes();
        Set<Point> empty = new HashSet<>(viz.drawGUI());
        setup();
        useShapes(tri(5000, 5000, 5100, 5000, 5000, 5100));
        Set<Point> far = new HashSet<>(viz.drawGUI());
        assertEquals(empty, far);
    }

    @Test
    @DisplayName("drawGUI: spec Figure 3 triangle shows its visible hypotenuse at GUI (200,300)-(250,250)")
    void draw_figure3_hypotenuse() {
        useShapes(figure3());
        List<Point> px = viz.drawGUI();
        // hypotenuse is the line x + y = 500 in GUI coordinates
        int n = pixelsNearLine(px, 500, 212, 248);
        assertTrue(n >= 10, "expected the hypotenuse (x + y = 500) to be drawn; only " + n + " pixels found near it");
    }

    @Test
    @DisplayName("drawGUI: after each key the environment shifts 5 pixels the opposite way")
    void draw_shifts_after_move() {
        // moving D (+x) or S (+y) shifts the scene left/up, so the hypotenuse moves to
        // x + y = 495;
        // moving A (-x) or W (-y) shifts it right/down, so x + y = 505
        Object[][] cases = { { 'D', 495.0 }, { 'S', 495.0 }, { 'A', 505.0 }, { 'W', 505.0 } };
        for (Object[] cs : cases) {
            char key = (Character) cs[0];
            double line = (Double) cs[1];
            setup();
            useShapes(figure3());
            assertTrue(viz.moveSquare(key), "key " + key + " should be able to move");
            int n = pixelsNearLine(viz.drawGUI(), line, 215, 245);
            assertTrue(n >= 8, "after '" + key + "' expected the hypotenuse on x + y = " + line + ", found only " + n
                    + " pixels near it");
        }
    }

    @Nested
    @Tag("unspecified")
    class UnspecifiedBehavior {

        @Test
        @DisplayName("[unspecified] moveSquare returns false when no BVH has been set")
        void move_requires_bvh() {
            assertFalse(viz.moveSquare('W'));
        }

        @Test
        @DisplayName("[unspecified] drawGUI of an empty scene is non-empty (square outline is drawn)")
        void draw_empty_scene_has_square_outline() {
            useShapes();
            LinkedList<Point> pixels = viz.drawGUI();
            assertFalse(pixels.isEmpty(), "square outline should produce pixels even with no shapes");
        }

        @Test
        @DisplayName("[unspecified] lowercase keys are accepted")
        void lowercase_keys() {
            useShapes();
            assertTrue(viz.moveSquare('d'));
        }
    }
}