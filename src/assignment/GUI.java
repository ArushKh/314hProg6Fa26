package assignment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class GUI {
    public static Visualization visualization = new Visualization();

    public static void main(String[] args) {
        List<Shape> myTriangles = new ArrayList<>();
        myTriangles.add(new Triangle(new Point(100, 100), new Point(150, 100), new Point(100, 150)));
        myTriangles.add(new Triangle(new Point(-50, -50), new Point(-10, -50), new Point(-50, -10)));
        myTriangles.add(new Triangle(new Point(50, 50), new Point(80, 70), new Point(20, 90)));

        BoundedVolumeHierarchy bvh = new BoundedVolumeHierarchy();
        bvh.buildBVH(myTriangles);
        visualization.setBoundingVolumeHierarchy(bvh);

        JFrame frame = new JFrame("BVH Visualization");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400);
        BVHVisualization bvhVisualization = new BVHVisualization();
        frame.add(bvhVisualization);
        frame.setVisible(true);
        frame.setResizable(false);
    }
}

class BVHVisualization extends JPanel {
    public BVHVisualization() {
        this.setFocusable(true);
        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (GUI.visualization.moveSquare(e.getKeyChar())) {
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK);
        LinkedList<Point> points = GUI.visualization.drawGUI();
        for (Point point : points) {
            g.fillRect(point.x, point.y, 1, 1);
        }
        g.setColor(Color.BLUE);
        g.fillRect(190, 190, 21, 21);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 400);
    }
}
