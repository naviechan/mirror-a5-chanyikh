package edu.toronto.csc301.warehouse;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;

import edu.toronto.csc301.grid.GridCell;
import edu.toronto.csc301.robot.IGridRobot;
import edu.toronto.csc301.robot.IGridRobot.Direction;
import edu.toronto.csc301.warehouse.PathPlanner.Node;

public class PathPlanner implements IPathPlanner {

		
	private IWarehouse house;
	// Current BFS stack. Function: Add new node to the stack, pop the first node in stack and expand etc.
	private Queue<Node> BFS_stack = new LinkedList<Node>();
	// Keep track of all gridcells that the robot has been visited, to prevent cycle
	private List<GridCell> node_visited = new ArrayList<GridCell>();
	public PathPlanner(){
		
	}
	
	// This is node of the tree
	public class Node{
		
		Node parent;
		GridCell cell;
		List<Node> children = new ArrayList<Node>();
		public Node(GridCell location, Node p){
			this.parent = p;
			this.cell = location;
		}
		public Node getParent(){
			return this.parent;
		}
		public GridCell getLocation(){
			return this.cell;
		}
		public void addChild(Node c){
			children.add(c);
			return;
		}
		public List<Node> getChildren(){
			return this.children;
		}
		
	}
	
	@Override
	public Entry<IGridRobot, Direction> nextStep(IWarehouse warehouse, Map<IGridRobot, GridCell> robot2dest) {
		this.house = warehouse;
		Entry<IGridRobot, GridCell> entry = robot2dest.entrySet().iterator().next();
		IGridRobot robot = entry.getKey();
		GridCell dest = entry.getValue();
		// Check if robot is at its destination
		if (dest.equals(robot.getLocation())){
			return null;
		}
		// Add root to the stack
		Node root = new Node(robot.getLocation(), null);
		BFS_stack.add(root);
		Direction direction = BFS(root, dest);
		Entry<IGridRobot, Direction> result = new AbstractMap.SimpleEntry<IGridRobot, Direction>(robot, direction);
		
		BFS_stack = new LinkedList<Node>();
		node_visited = new ArrayList<GridCell>();
		return result;
	}

	public Direction BFS(Node root, GridCell dest){
		Node current_node = new Node(null, null);
		while (BFS_stack.size() != 0){
			// Pop the first node in the stack
			current_node = BFS_stack.poll();
			// Check if this node is our dest
			if (dest.equals(current_node.getLocation())){
				break;
			}
			
			// Expand it and insert all its children into the stack
			List<Node> children = findChildren(current_node);
			// Add all children into the stack
			for (Node child : children){
				BFS_stack.add(child);
			}

			// Add current_node into node_visited
			node_visited.add(current_node.getLocation());
		}
		
		// Return error if we cant find anything
		if (current_node.getParent() == null){
			throw new IllegalArgumentException();
		}
		
		// Start doing a backtrace
		// Trace back until a node's parent is root
		while (current_node.getParent() != root){
			current_node = current_node.getParent();

		}
		// Now we have current_node which its parent is root
		// Find the direction
		GridCell root_location = root.getLocation();
		GridCell next_step_location = current_node.getLocation();
		int root_x = root_location.x;
		int root_y = root_location.y;
		int next_x = next_step_location.x;
		int next_y = next_step_location.y;
		Direction result = null;
		if (next_x == root_x + 1){
			result = Direction.EAST;
		}
		else if (next_x == root_x - 1){
			result = Direction.WEST;
		}
		else if (next_y == root_y + 1){
			result = Direction.NORTH;
		}
		else if (next_y == root_y - 1){
			result = Direction.SOUTH;
		}
		
		return result;
		
	}

	// Check if cell goes out of grid, or if cell has another robot on it
	// Return true if cell is illegal
	public boolean checkIllegalCell(GridCell cell){
		// If floor plan does not have this cell
		if (!this.house.getFloorPlan().hasCell(cell)){
			return true;
		}
		// Check if robot is on that cell
		Iterator<IGridRobot> robots = this.house.getRobots();
		while (robots.hasNext()){
			IGridRobot current_robot = robots.next();
			if	(cell.equals(current_robot.getLocation())){
				return true;
			}
		}
		// Check if we are trap in cycle
		if (node_visited.contains(cell)){
			return true;
		}
		
		return false;
	}
	
	
	// Return a list of children nodes given a node
	public List<Node> findChildren(Node current){
		List<Node> result = new ArrayList<Node>();
		List<GridCell> possibleLocation = new ArrayList<GridCell>();
		GridCell node_location = current.getLocation();
		int x = node_location.x;
		int y = node_location.y;
		// In this order: Up left right down
		possibleLocation.add(GridCell.at(x, y + 1));
		possibleLocation.add(GridCell.at(x - 1, y));
		possibleLocation.add(GridCell.at(x + 1, y));		
		possibleLocation.add(GridCell.at(x, y - 1));

		for (int i = 0; i < possibleLocation.size(); i++){
			GridCell location = possibleLocation.get(i);
			if (checkIllegalCell(location)){
				possibleLocation.set(i, null);
			}
		}
		

		for (GridCell location : possibleLocation){
			if (location != null) {
				Node new_node = new Node(location, current);
				result.add(new_node);
			}
		}
		
		return result;
	}
	
	// Debug function, print out stuff inside node
	public void printNode(Node n){
		String s = "Location: " + n.getLocation().toString(); 
		System.out.println(s);
	}
	
	public void printStack(){
		String s = "BFS_stack: ";
		for (Node n : BFS_stack){
			s = s + n.getLocation() + " ";
		}
		System.out.println(s);
	}
	
}
