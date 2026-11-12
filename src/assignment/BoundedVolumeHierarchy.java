// File: src/assignment/BoundedVolumeHierarchy.java
package assignment;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

public class BoundedVolumeHierarchy implements BVH {

  private SplitMethod splitMethod = SplitMethod.SPLIT_MEDIAN;
  private final Random rng = new Random(1337);

  private static final class Node {
    BoundingBox box;
    Shape leafShape;
    Node left, right;
    int height;

    Node(Shape shape) {
      this.leafShape = shape;
      this.box = BoundingBox.fromShape(shape);
      this.height = 1;
    }

    Node(Node leftChild, Node rightChild) {
      this.left = leftChild;
      this.right = rightChild;
      recompute();
    }

    boolean isLeaf() {
      return leafShape != null;
    }

    void recompute() {
      if (isLeaf()) {
        box = BoundingBox.fromShape(leafShape);
        height = 1;
      } else {
        box = BoundingBox.combine(left.box, right.box);
        int leftHeight = height(left);
        int rightHeight = height(right);
        int maxChildHeight = (rightHeight > leftHeight) ? rightHeight : leftHeight;
        height = 1 + maxChildHeight;
      }
    }

    static int height(Node node) {
      if (node == null)
        return 0;
      return node.height;
    }

    static int balance(Node node) {
      if (node == null)
        return 0;
      return height(node.left) - height(node.right);
    }
  }

  private static final class BoundingBox {
    final int minX, minY, maxX, maxY;

    static BoundingBox fromShape(Shape shape) {
      Point minPoint = shape.getMinSurroundingPoint();
      Point maxPoint = shape.getMaxSurroundingPoint();
      return new BoundingBox(minPoint.x, minPoint.y, maxPoint.x, maxPoint.y);
    }

    static BoundingBox combine(BoundingBox a, BoundingBox b) {
      int combinedMinX = Math.min(a.minX, b.minX);
      int combinedMinY = Math.min(a.minY, b.minY);
      int combinedMaxX = Math.max(a.maxX, b.maxX);
      int combinedMaxY = Math.max(a.maxY, b.maxY);
      return new BoundingBox(combinedMinX, combinedMinY, combinedMaxX, combinedMaxY);
    }

    BoundingBox(int minX, int minY, int maxX, int maxY) {
      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
    }

    long area() {
      long width = Math.max(0, maxX - minX);
      long height = Math.max(0, maxY - minY);
      return width * height;
    }

    boolean contains(Point2D.Double point) {
      if (point.x < minX)
        return false;
      if (point.x > maxX)
        return false;
      if (point.y < minY)
        return false;
      if (point.y > maxY)
        return false;
      return true;
    }

    boolean intersectsRay(Point origin, Vector2D direction) {
      Rectangle rect = new Rectangle(new Point(minX, minY), new Point(maxX, maxY));
      return rect.doesRayIntersect(origin, direction);
    }

    @Override
    public String toString() {
      return "[(" + minX + ", " + minY + "), (" + maxX + ", " + maxY + ")]";
    }
  }

  private Node root;
  private final Set<Shape> shapeSet = new HashSet<>();

  @Override
  public void setSplitMethod(SplitMethod splitMethod) {
    if (splitMethod == null)
      throw new IllegalArgumentException("split method null");
    this.splitMethod = splitMethod;
  }

  @Override
  public void buildBVH(List<Shape> shapeList) {
    if (shapeList == null)
      throw new IllegalArgumentException("shape list null");

    for (Shape shape : shapeList) {
      if (shape == null)
        throw new IllegalArgumentException("shape is null");
    }

    HashSet<Shape> distinctShapes = new HashSet<>(shapeList);
    if (distinctShapes.size() != shapeList.size())
      throw new IllegalArgumentException("duplicate shapes not allowed");

    this.shapeSet.clear();
    this.shapeSet.addAll(distinctShapes);

    this.root = buildRecursive(new ArrayList<>(distinctShapes), 0);
  }

  @Override
  public void insert(Shape shape) {
    if (shape == null)
      throw new IllegalArgumentException("shape null");
    if (shapeSet.contains(shape))
      throw new IllegalArgumentException("duplicate shape");
    shapeSet.add(shape);
    root = insertAt(root, new Node(shape));
  }

  @Override
  public void remove(Shape shape) {
    if (shape == null)
      throw new IllegalArgumentException("shape null");
    if (!shapeSet.contains(shape))
      return;
    root = removeAt(root, shape);
    shapeSet.remove(shape);
  }

  @Override
  public Set<Shape> findCollision(Point2D.Double point) {
    if (point == null)
      throw new IllegalArgumentException("point null");
    HashSet<Shape> collisions = new HashSet<>();
    findCollision(root, point, collisions);
    return collisions;
  }

  @Override
  public Shape intersectRay(Point2D.Double origin, Vector2D direction) {
    if (origin == null || direction == null)
      throw new IllegalArgumentException("null args");

    Hit closestHit = intersectRay(root, new Point((int) origin.x, (int) origin.y), direction, origin);
    if (closestHit == null)
      return null;

    return closestHit.shape;
  }

  @Override
  public String toString() {
    StringBuilder output = new StringBuilder();
    preorder(root, 0, output);
    return output.toString();
  }

  public List<Shape> getAllShapes() {
    return new ArrayList<>(shapeSet);
  }

  private Node buildRecursive(List<Shape> shapes, int depth) {
    if (shapes.isEmpty())
      return null;

    if (shapes.size() == 1)
      return new Node(shapes.get(0));

    int axis = depth & 1;

    if (splitMethod == SplitMethod.SPLIT_MIDRANGE) {

      double minCoord = Double.POSITIVE_INFINITY;
      double maxCoord = Double.NEGATIVE_INFINITY;

      for (Shape shape : shapes) {
        Point2D.Double center = shape.getCenter();
        double coord = (axis == 0) ? center.x : center.y;
        if (coord < minCoord)
          minCoord = coord;
        if (coord > maxCoord)
          maxCoord = coord;
      }

      double dividingLine = (minCoord + maxCoord) / 2.0;
      splitByLine(shapes, axis, dividingLine);

    } else {
      List<Shape> sortedShapes = new ArrayList<>(shapes);

      sortedShapes.sort((a, b) -> {
        double coordA = (axis == 0) ? a.getCenter().x : a.getCenter().y;
        double coordB = (axis == 0) ? b.getCenter().x : b.getCenter().y;
        return Double.compare(coordA, coordB);
      });

      int midIndex = sortedShapes.size() / 2;
      double dividingLine = (axis == 0)
          ? sortedShapes.get(midIndex).getCenter().x
          : sortedShapes.get(midIndex).getCenter().y;

      shapes = sortedShapes;
      splitByLine(shapes, axis, dividingLine);
    }

    int total = shapes.size();
    int leftCount = leftCount(shapes);
    int rightCount = total - leftCount - 1;

    if (leftCount == 0 || rightCount == 0) {

      List<Shape> nonNullShapes = shapes.stream()
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      int nearestIndex = nearestToSplit(nonNullShapes, depth);

      List<Shape> leftList = new ArrayList<>();
      List<Shape> rightList = new ArrayList<>(nonNullShapes);

      Shape movedShape = rightList.remove(nearestIndex);
      leftList.add(movedShape);

      Node leftNode = buildRecursive(leftList, depth + 1);
      Node rightNode = buildRecursive(rightList, depth + 1);

      return new Node(leftNode, rightNode);
    }

    List<Shape> leftShapes = new ArrayList<>(shapes.subList(0, leftCount));
    List<Shape> rightShapes = new ArrayList<>(shapes.subList(leftCount + 1, total));

    Node leftNode = buildRecursive(leftShapes, depth + 1);
    Node rightNode = buildRecursive(rightShapes, depth + 1);

    return new Node(leftNode, rightNode);
  }

  private static int leftCount(List<Shape> shapes) {
    int index = 0;
    while (index < shapes.size() && shapes.get(index) != null)
      index++;
    return index;
  }

  private static int nearestToSplit(List<Shape> shapes, int depth) {
    int axis = depth & 1;

    List<Double> centers = new ArrayList<>();
    for (Shape shape : shapes) {
      if (shape != null) {
        double coord = (axis == 0) ? shape.getCenter().x : shape.getCenter().y;
        centers.add(coord);
      }
    }

    double sum = 0.0;
    for (double coord : centers)
      sum += coord;
    double dividingLine = centers.isEmpty() ? 0.0 : sum / centers.size();

    double bestDist = Double.POSITIVE_INFINITY;
    int bestIndex = 0;

    for (int i = 0; i < shapes.size(); i++) {
      Shape shape = shapes.get(i);
      if (shape == null)
        continue;

      double coord = (axis == 0) ? shape.getCenter().x : shape.getCenter().y;
      double dist = Math.abs(coord - dividingLine);

      if (dist < bestDist) {
        bestDist = dist;
        bestIndex = i;
      }
    }

    return bestIndex;
  }

  private static void splitByLine(List<Shape> shapes, int axis, double line) {

    shapes.sort((a, b) -> {
      double coordA = (axis == 0) ? a.getCenter().x : a.getCenter().y;
      double coordB = (axis == 0) ? b.getCenter().x : b.getCenter().y;
      return Double.compare(coordA, coordB);
    });

    List<Shape> leftList = new ArrayList<>();
    List<Shape> rightList = new ArrayList<>();

    for (Shape shape : shapes) {
      double coord = (axis == 0) ? shape.getCenter().x : shape.getCenter().y;

      if (coord < line) {
        leftList.add(shape);
      } else if (coord > line) {
        rightList.add(shape);
      } else {
        if (leftList.size() <= rightList.size()) {
          leftList.add(shape);
        } else {
          rightList.add(shape);
        }
      }
    }

    shapes.clear();
    shapes.addAll(leftList);
    shapes.add(null);
    shapes.addAll(rightList);
  }

  private Node insertAt(Node node, Node newLeaf) {
    if (node == null)
      return newLeaf;

    if (node.isLeaf()) {
      Node newParent = new Node(node, newLeaf);
      return rebalance(newParent);
    }

    if (node.left == null) {
      node.left = newLeaf;
      node.recompute();
      return rebalance(node);
    }

    if (node.right == null) {
      node.right = newLeaf;
      node.recompute();
      return rebalance(node);
    }

    Node chosenChild = chooseChild(node, newLeaf.leafShape);
    if (chosenChild == node.left)
      node.left = insertAt(node.left, newLeaf);
    else
      node.right = insertAt(node.right, newLeaf);

    node.recompute();
    return rebalance(node);
  }

  private Node chooseChild(Node parent, Shape newShape) {
    boolean leftLeaf = parent.left.isLeaf();
    boolean rightLeaf = parent.right.isLeaf();

    if (leftLeaf && !rightLeaf)
      return parent.left;
    if (rightLeaf && !leftLeaf)
      return parent.right;

    BoundingBox expandedLeft = BoundingBox.combine(parent.left.box, BoundingBox.fromShape(newShape));
    BoundingBox expandedRight = BoundingBox.combine(parent.right.box, BoundingBox.fromShape(newShape));

    long leftIncrease = expandedLeft.area() - parent.left.box.area();
    long rightIncrease = expandedRight.area() - parent.right.box.area();

    if (leftIncrease < rightIncrease)
      return parent.left;
    if (rightIncrease < leftIncrease)
      return parent.right;

    int leftHeight = Node.height(parent.left);
    int rightHeight = Node.height(parent.right);

    if (leftHeight < rightHeight)
      return parent.left;
    if (rightHeight < leftHeight)
      return parent.right;

    return rng.nextBoolean() ? parent.left : parent.right;
  }

  private Node removeAt(Node node, Shape target) {
    if (node == null)
      return null;

    if (node.isLeaf()) {
      if (node.leafShape.equals(target))
        return null;
      return node;
    }

    node.left = removeAt(node.left, target);
    node.right = removeAt(node.right, target);

    if (node.left == null)
      return node.right;
    if (node.right == null)
      return node.left;

    node.recompute();
    return rebalance(node);
  }

  private Node rebalance(Node node) {
    if (node == null)
      return null;

    node.recompute();
    int balanceFactor = Node.balance(node);

    if (balanceFactor > 1) {
      if (Node.balance(node.left) < 0)
        node.left = rotateLeft(node.left);
      return rotateRight(node);
    }

    if (balanceFactor < -1) {
      if (Node.balance(node.right) > 0)
        node.right = rotateRight(node.right);
      return rotateLeft(node);
    }

    return node;
  }

  private Node rotateRight(Node unbalanced) {
    Node newRoot = unbalanced.left;
    Node movedSubtree = newRoot.right;

    newRoot.right = unbalanced;
    unbalanced.left = movedSubtree;

    unbalanced.recompute();
    newRoot.recompute();
    return newRoot;
  }

  private Node rotateLeft(Node unbalanced) {
    Node newRoot = unbalanced.right;
    Node movedSubtree = newRoot.left;

    newRoot.left = unbalanced;
    unbalanced.right = movedSubtree;

    unbalanced.recompute();
    newRoot.recompute();
    return newRoot;
  }

  private void findCollision(Node node, Point2D.Double point, Set<Shape> out) {
    if (node == null)
      return;
    if (!node.box.contains(point))
      return;

    if (node.isLeaf()) {
      if (node.leafShape.containsPoint(point))
        out.add(node.leafShape);
      return;
    }

    findCollision(node.left, point, out);
    findCollision(node.right, point, out);
  }

  private static final class Hit {
    final Shape shape;
    final double dist;

    Hit(Shape shape, double distance) {
      this.shape = shape;
      this.dist = distance;
    }
  }

  private Hit intersectRay(Node node, Point originInt, Vector2D direction,
      Point2D.Double originDouble) {
    if (node == null)
      return null;
    if (!node.box.intersectsRay(originInt, direction))
      return null;

    if (node.isLeaf()) {
      Point2D.Double intersectionPoint = node.leafShape.findIntersection(originInt, direction);
      if (intersectionPoint == null)
        return null;
      double distance = BVH.distanceBetweenPoints(originDouble, intersectionPoint);
      return new Hit(node.leafShape, distance);
    }

    Hit leftHit = intersectRay(node.left, originInt, direction, originDouble);
    Hit rightHit = intersectRay(node.right, originInt, direction, originDouble);

    if (leftHit == null && rightHit == null)
      return null;
    if (leftHit == null)
      return rightHit;
    if (rightHit == null)
      return leftHit;

    return (leftHit.dist <= rightHit.dist) ? leftHit : rightHit;
  }

  private void preorder(Node node, int depth, StringBuilder sb) {
    if (node == null)
      return;

    for (int i = 0; i < depth; i++)
      sb.append('\t');

    if (node.isLeaf()) {
      sb.append(node.leafShape.toString());
    } else {
      sb.append(node.box.toString()).append('\n');
      preorder(node.left, depth + 1, sb);
      preorder(node.right, depth + 1, sb);
    }
  }
}
