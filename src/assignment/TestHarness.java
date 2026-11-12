// // src/assignment/TestHarness.java
// package assignment;

// import java.awt.Point; // <-- add this
// import java.util.ArrayList;
// import java.util.LinkedList;
// import java.util.List;

// public class TestHelper {
//   public static void main(String[] args) {
//     List<Shape> tris = new ArrayList<>();
//     tris.add(new Triangle(new Point(-200, -150), new Point(-120, -60), new Point(-40, -200)));
//     tris.add(new Triangle(new Point(140, 90), new Point(220, 140), new Point(180, 10)));
//     tris.add(new Triangle(new Point(-50, 260), new Point(60, 240), new Point(10, 180)));

//     BoundedVolumeHierarchy bvh = new BoundedVolumeHierarchy(); // comes from the jar
//     bvh.buildBVH(tris);

//     GUI.visualization.setBoundingVolumeHierarchy(bvh);

//     LinkedList<Point> pixels = GUI.visualization.drawGUI();
//     System.out.println("Pixels drawn: " + pixels.size());
//   }
// }
