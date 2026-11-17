// File: src/assignment/Visualization.java
package assignment;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Visualization {

    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 400;
    private static final int SQUARE_SIZE = 21;
    private static final int MOVE_DELTA = 5;

    private BoundedVolumeHierarchy bvh;
    private Point2D.Double squarePos = new Point2D.Double(0, 0);

    public void setBoundingVolumeHierarchy(BoundedVolumeHierarchy bvh) {
        this.bvh = bvh;
    }

    public boolean moveSquare(char keyPressed) {
        if (bvh == null)
            return false;

        double dx = 0.0;
        double dy = 0.0;

        char c = Character.toUpperCase(keyPressed);
        if (c == 'W')
            dy = -MOVE_DELTA;
        else if (c == 'S')
            dy = MOVE_DELTA;
        else if (c == 'A')
            dx = -MOVE_DELTA;
        else if (c == 'D')
            dx = MOVE_DELTA;
        else
            return false;

        Point2D.Double next = new Point2D.Double(squarePos.x + dx, squarePos.y + dy);

        int half = SQUARE_SIZE / 2;
        double minX = next.x - half, maxX = next.x + half;
        double minY = next.y - half, maxY = next.y + half;

        for (Shape s : bvh.getAllShapes()) {
            if (!(s instanceof Triangle))
                continue;
            Point mn = s.getMinSurroundingPoint();
            Point mx = s.getMaxSurroundingPoint();
            if (mx.x < minX || mn.x > maxX || mx.y < minY || mn.y > maxY)
                continue;
            if (squareTouchesTriangle((Triangle) s, minX, minY, maxX, maxY))
                return false;
        }

        squarePos = next;
        return true;
    }

    private boolean squareTouchesTriangle(Triangle t, double minX, double minY, double maxX, double maxY) {
        Point2D.Double[] tri = {
                new Point2D.Double(t.a.getX(), t.a.getY()),
                new Point2D.Double(t.b.getX(), t.b.getY()),
                new Point2D.Double(t.c.getX(), t.c.getY()) };
        Point2D.Double[] sq = {
                new Point2D.Double(minX, minY), new Point2D.Double(maxX, minY),
                new Point2D.Double(maxX, maxY), new Point2D.Double(minX, maxY) };

        for (Point2D.Double p : tri)
            if (p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY)
                return true;
        for (Point2D.Double p : sq)
            if (pointInTriangle(p, tri))
                return true;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 4; j++)
                if (segmentsIntersect(tri[i], tri[(i + 1) % 3], sq[j], sq[(j + 1) % 4]))
                    return true;
        return false;
    }

    private static double cross(Point2D a, Point2D b, Point2D c) {
        return (b.getX() - a.getX()) * (c.getY() - a.getY()) - (b.getY() - a.getY()) * (c.getX() - a.getX());
    }

    private static boolean pointInTriangle(Point2D p, Point2D[] t) {
        double d1 = cross(t[0], t[1], p), d2 = cross(t[1], t[2], p), d3 = cross(t[2], t[0], p);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static boolean onSegment(Point2D a, Point2D b, Point2D p) {
        return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY());
    }

    private static boolean segmentsIntersect(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        double d1 = cross(p3, p4, p1), d2 = cross(p3, p4, p2);
        double d3 = cross(p1, p2, p3), d4 = cross(p1, p2, p4);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0)))
            return true;
        return (d1 == 0 && onSegment(p3, p4, p1)) || (d2 == 0 && onSegment(p3, p4, p2))
                || (d3 == 0 && onSegment(p1, p2, p3)) || (d4 == 0 && onSegment(p1, p2, p4));
    }

    public LinkedList<Point> drawGUI() {
        LinkedList<Point> pixels = new LinkedList<>();
        if (bvh == null)
            return pixels;

        double cx = GUI_WIDTH / 2.0;
        double cy = GUI_HEIGHT / 2.0;

        double halfW = GUI_WIDTH / 2.0;
        double halfH = GUI_HEIGHT / 2.0;
        double minX = squarePos.x - halfW;
        double maxX = squarePos.x + halfW;
        double minY = squarePos.y - halfH;
        double maxY = squarePos.y + halfH;

        List<Shape> shapes = bvh.getAllShapes();

        for (Shape s : shapes) {
            Point mn = s.getMinSurroundingPoint();
            Point mx = s.getMaxSurroundingPoint();

            // AABB-viewport reject for efficiency
            if (mx.x < minX || mn.x > maxX || mx.y < minY || mn.y > maxY)
                continue;

            if (s instanceof Triangle) {
                Triangle t = (Triangle) s;

                Point2D.Double[] verts = new Point2D.Double[3];
                verts[0] = new Point2D.Double(t.a.getX(), t.a.getY());
                verts[1] = new Point2D.Double(t.b.getX(), t.b.getY());
                verts[2] = new Point2D.Double(t.c.getX(), t.c.getY());

                // Draw edges that are visible via ray casting
                drawVisibleEdge(verts[0], verts[1], squarePos, pixels, cx, cy);
                drawVisibleEdge(verts[1], verts[2], squarePos, pixels, cx, cy);
                drawVisibleEdge(verts[2], verts[0], squarePos, pixels, cx, cy);
            }
        }

        return pixels;
    }

    private void drawVisibleEdge(Point2D.Double v1, Point2D.Double v2, Point2D.Double observer,
            LinkedList<Point> out, double cx, double cy) {
        int numSamples = (int) Math.max(Math.abs(v2.x - v1.x), Math.abs(v2.y - v1.y)) * 2 + 10;

        for (int i = 0; i <= numSamples; i++) {
            double t = i / (double) numSamples;
            double px = v1.x + t * (v2.x - v1.x);
            double py = v1.y + t * (v2.y - v1.y);
            Point2D.Double edgePoint = new Point2D.Double(px, py);

            double dx = edgePoint.x - observer.x;
            double dy = edgePoint.y - observer.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 0.001)
                continue;

            Vector2D dir = new Vector2D(dx / dist, dy / dist);

            Point2D.Double origin = new Point2D.Double(observer.x, observer.y);
            Shape hit = bvh.intersectRay(origin, dir);

            if (hit != null) {
                Point originInt = new Point((int) observer.x, (int) observer.y);
                Point2D.Double intersection = hit.findIntersection(originInt, dir);

                if (intersection != null) {
                    double intersectDist = Math.sqrt(
                            Math.pow(intersection.x - observer.x, 2) +
                                    Math.pow(intersection.y - observer.y, 2));

                    // If the intersection is close to this edge point, it's visible
                    if (Math.abs(intersectDist - dist) < 0.01) {
                        Point guiPoint = worldToGUI(edgePoint, cx, cy);
                        if (guiPoint.x >= 0 && guiPoint.x < GUI_WIDTH &&
                                guiPoint.y >= 0 && guiPoint.y < GUI_HEIGHT) {
                            out.add(guiPoint);
                        }
                    }
                }
            }
        }
    }

    private Point worldToGUI(Point2D.Double v, double cx, double cy) {
        double gx = cx + (v.x - squarePos.x);
        double gy = cy + (v.y - squarePos.y);
        return new Point((int) Math.round(gx), (int) Math.round(gy));
    }

    private void drawLine(Point p1, Point p2, LinkedList<Point> out) {
        int x1 = p1.x, y1 = p1.y;
        int x2 = p2.x, y2 = p2.y;

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = -1;
        if (x1 < x2)
            sx = 1;

        int sy = -1;
        if (y1 < y2)
            sy = 1;

        int err = dx - dy;

        while (true) {
            if (x1 >= 0 && x1 < GUI_WIDTH && y1 >= 0 && y1 < GUI_HEIGHT) {
                out.add(new Point(x1, y1));
            }
            if (x1 == x2 && y1 == y2)
                break;

            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private void addSquareOutline(LinkedList<Point> out, double cx, double cy) {
        int half = SQUARE_SIZE / 2;
        int L = (int) (cx - half);
        int R = (int) (cx + half);
        int T = (int) (cy - half);
        int B = (int) (cy + half);

        for (int x = L; x <= R; x++) {
            out.add(new Point(x, T));
            out.add(new Point(x, B));
        }
        for (int y = T; y <= B; y++) {
            out.add(new Point(L, y));
            out.add(new Point(R, y));
        }
    }
}