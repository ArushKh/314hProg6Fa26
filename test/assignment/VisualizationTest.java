// test/assignment/VisualizationTest.java
package assignment;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VisualizationTest {

    /** Tiny stub so we can control collisions and shapes. */
    static class StubBVH extends BoundedVolumeHierarchy {
        boolean blockNextMove = false;
        List<Shape> shapes = Collections.emptyList();

        @Override
        public Set<Shape> findCollision(Point2D.Double p) {
            return blockNextMove
                    ? new HashSet<>(
                            Collections.singleton(new Triangle(new Point(0, 0), new Point(1, 0), new Point(0, 1))))
                    : Collections.emptySet();
        }

        @Override
        public List<Shape> getAllShapes() {
            return new ArrayList<>(shapes);
        }
    }

    private Visualization viz;
    private StubBVH bvh;

    @BeforeEach
    void setup() {
        viz = new Visualization();
        bvh = new StubBVH();
    }

    @Test
    void moveSquare_requiresBVH() {
        // No BVH set yet -> cannot move
        assertFalse(viz.moveSquare('W'));
    }

    @Test
    void moveSquare_movesWhenClear_blocksWhenColliding() {
        viz.setBoundingVolumeHierarchy(bvh);

        // Clear path -> should move
        bvh.blockNextMove = false;
        assertTrue(viz.moveSquare('D'));

        // Now force a collision -> should not move
        bvh.blockNextMove = true;
        assertFalse(viz.moveSquare('W'));
    }

    @Test
    void drawGUI_returnsNonEmpty_dueToSquareOutline() {
        viz.setBoundingVolumeHierarchy(bvh);
        LinkedList<Point> pixels = viz.drawGUI();
        assertNotNull(pixels);
        assertFalse(pixels.isEmpty(), "square outline should produce pixels even with no shapes");
    }
}
