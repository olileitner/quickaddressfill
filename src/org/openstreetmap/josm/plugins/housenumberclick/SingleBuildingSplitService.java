package org.openstreetmap.josm.plugins.housenumberclick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SplitWayCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

final class SingleBuildingSplitService {

    private final CornerSnapService cornerSnapService;
    private final SplitCommandBuilder splitCommandBuilder;

    SingleBuildingSplitService() {
        this(new CornerSnapService(), new SplitCommandBuilder());
    }

    SingleBuildingSplitService(CornerSnapService cornerSnapService, SplitCommandBuilder splitCommandBuilder) {
        this.cornerSnapService = cornerSnapService;
        this.splitCommandBuilder = splitCommandBuilder;
    }

    SingleSplitResult splitBuilding(DataSet dataSet, Way buildingWay, LatLon lineStart, LatLon lineEnd, SplitContext context) {
        final int undoStartSize = UndoRedoHandler.getInstance().getUndoCommands().size();
        if (dataSet == null) {
            return SingleSplitResult.failure("No editable dataset is available.");
        }
        if (buildingWay == null) {
            return SingleSplitResult.failure("No building way selected.");
        }
        if (buildingWay.getDataSet() != dataSet) {
            return SingleSplitResult.failure("Selected building is not part of the current dataset.");
        }
        if (!buildingWay.isClosed()) {
            return SingleSplitResult.failure("The selected way must be closed.");
        }
        if (!buildingWay.hasKey("building")) {
            return SingleSplitResult.failure("The selected way must have a building=* tag.");
        }

        IntersectionScanResult intersectionResult = cornerSnapService.findSplitIntersections(buildingWay, lineStart, lineEnd);
        if (!intersectionResult.isSuccess()) {
            return SingleSplitResult.failure(intersectionResult.getMessage());
        }

        List<IntersectionPoint> intersections = intersectionResult.getIntersections();
        if (intersections.isEmpty()) {
            return SingleSplitResult.failure("Split line does not intersect selected building.");
        }
        if (intersections.size() == 1) {
            return SingleSplitResult.failure("Split line touches selected building only once.");
        }
        if (intersections.size() > 2) {
            return SingleSplitResult.failure("Split line intersects selected building more than twice.");
        }

        SplitCommandBuilder.PreparedNodeCommands prepared = splitCommandBuilder.prepareNodes(dataSet, buildingWay, intersections);
        List<Node> splitNodes = prepared.getSplitNodes();
        if (splitNodes.size() != 2 || splitNodes.get(0).equals(splitNodes.get(1))) {
            return SingleSplitResult.failure("Failed to resolve two distinct split nodes.");
        }
        if (areAdjacentInClosedNodeList(prepared.getUpdatedWayNodes(), splitNodes.get(0), splitNodes.get(1))) {
            return SingleSplitResult.failure("Split nodes are adjacent on the building outline.");
        }

        if (!prepared.getCommands().isEmpty()) {
            UndoRedoHandler.getInstance().add(
                    splitCommandBuilder.buildSequenceCommand("Insert split intersection nodes", prepared.getCommands())
            );
        }

        RingPaths ringPaths = extractRingPaths(buildingWay, splitNodes.get(0), splitNodes.get(1));
        if (ringPaths == null) {
            return rollbackAndFailure(undoStartSize, "Unable to compute split paths for selected building.");
        }

        List<Node> polygonANodes = buildClosedPolygon(ringPaths.pathFromFirstToSecond);
        List<Node> polygonBNodes = buildClosedPolygon(ringPaths.pathFromSecondToFirst);
        if (!isValidClosedPolygon(polygonANodes) || !isValidClosedPolygon(polygonBNodes)) {
            return rollbackAndFailure(undoStartSize, "Split points would create invalid polygons.");
        }

        List<List<Node>> splitChunks = Arrays.asList(polygonANodes, polygonBNodes);
        List<OsmPrimitive> splitSelection = Arrays.asList(buildingWay, splitNodes.get(0), splitNodes.get(1));
        Optional<SplitWayCommand> splitCommandOptional = splitCommandBuilder.createSplitWayCommand(buildingWay, splitChunks, splitSelection);
        if (splitCommandOptional.isEmpty()) {
            return rollbackAndFailure(undoStartSize, "Building split could not be executed.");
        }

        SplitWayCommand splitCommand = splitCommandOptional.get();
        List<Way> orderedResultWays = buildResultWaysOrdered(splitCommand.getOriginalWay(), splitCommand.getNewWays());

        List<Command> commands = new ArrayList<>();
        commands.add(splitCommand);
        UndoRedoHandler.getInstance().add(splitCommandBuilder.buildSequenceCommand("Split building", commands));

        return SingleSplitResult.success("Building split completed.", orderedResultWays);
    }

    private SingleSplitResult rollbackAndFailure(int undoStartSize, String message) {
        rollbackCommandsAddedSince(undoStartSize);
        return SingleSplitResult.failure(message);
    }

    private void rollbackCommandsAddedSince(int undoStartSize) {
        int undoCount = UndoRedoHandler.getInstance().getUndoCommands().size() - undoStartSize;
        if (undoCount > 0) {
            UndoRedoHandler.getInstance().undo(undoCount);
        }
    }

    private RingPaths extractRingPaths(Way way, Node firstNode, Node secondNode) {
        List<Node> ring = getOpenRing(way);
        if (ring == null || ring.size() < 3) {
            return null;
        }

        int firstIndex = ring.indexOf(firstNode);
        int secondIndex = ring.indexOf(secondNode);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex == secondIndex) {
            return null;
        }

        List<Node> pathFirstToSecond = collectPath(ring, firstIndex, secondIndex);
        List<Node> pathSecondToFirst = collectPath(ring, secondIndex, firstIndex);
        return new RingPaths(pathFirstToSecond, pathSecondToFirst);
    }

    private List<Node> getOpenRing(Way way) {
        List<Node> ring = new ArrayList<>(way.getNodes());
        if (ring.size() < 4) {
            return null;
        }
        Node first = ring.get(0);
        Node last = ring.get(ring.size() - 1);
        if (!first.equals(last)) {
            return null;
        }
        ring.remove(ring.size() - 1);
        return ring;
    }

    private List<Node> collectPath(List<Node> ring, int fromIndex, int toIndex) {
        List<Node> path = new ArrayList<>();
        int index = fromIndex;
        path.add(ring.get(index));
        while (index != toIndex) {
            index = (index + 1) % ring.size();
            path.add(ring.get(index));
        }
        return path;
    }

    private List<Node> buildClosedPolygon(List<Node> path) {
        List<Node> polygon = new ArrayList<>(path);
        if (polygon.isEmpty()) {
            return polygon;
        }
        Node startNode = polygon.get(0);
        Node endNode = polygon.get(polygon.size() - 1);
        if (!startNode.equals(endNode)) {
            polygon.add(startNode);
        }
        return polygon;
    }

    private boolean isValidClosedPolygon(List<Node> nodes) {
        if (nodes.size() < 4) {
            return false;
        }
        if (!nodes.get(0).equals(nodes.get(nodes.size() - 1))) {
            return false;
        }
        return nodes.subList(0, nodes.size() - 1).stream().distinct().count() >= 3;
    }

    private boolean areAdjacentInClosedNodeList(List<Node> closedWayNodes, Node firstNode, Node secondNode) {
        if (closedWayNodes == null || firstNode == null || secondNode == null || firstNode.equals(secondNode)) {
            return false;
        }

        List<Node> ring = new ArrayList<>(closedWayNodes);
        if (ring.size() < 4 || !ring.get(0).equals(ring.get(ring.size() - 1))) {
            return false;
        }

        ring.remove(ring.size() - 1);
        int firstIndex = ring.indexOf(firstNode);
        int secondIndex = ring.indexOf(secondNode);
        if (firstIndex < 0 || secondIndex < 0) {
            return false;
        }

        int diff = Math.abs(firstIndex - secondIndex);
        return diff == 1 || diff == ring.size() - 1;
    }

    private List<Way> buildResultWaysOrdered(Way originalWay, List<Way> newWays) {
        List<Way> ordered = new ArrayList<>();
        if (originalWay != null) {
            ordered.add(originalWay);
        }
        if (newWays != null) {
            ordered.addAll(newWays);
        }
        if (ordered.isEmpty()) {
            return Collections.emptyList();
        }
        return ordered;
    }

    private static final class RingPaths {
        private final List<Node> pathFromFirstToSecond;
        private final List<Node> pathFromSecondToFirst;

        private RingPaths(List<Node> pathFromFirstToSecond, List<Node> pathFromSecondToFirst) {
            this.pathFromFirstToSecond = pathFromFirstToSecond;
            this.pathFromSecondToFirst = pathFromSecondToFirst;
        }
    }
}

