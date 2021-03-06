package edu.toronto.csc301.warehouse;

import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import edu.toronto.csc301.grid.GridCell;
import edu.toronto.csc301.grid.IGrid;
import edu.toronto.csc301.robot.GridRobot;
import edu.toronto.csc301.robot.IGridRobot;
import edu.toronto.csc301.robot.IGridRobot.Direction;

public class PathPlanner implements IPathPlanner{
	
	private IWarehouse house;
	private List<IGridRobot> movingRobots = new ArrayList<IGridRobot>();
	private List<IGridRobot> idleRobots = new ArrayList<IGridRobot>();
	private List<GridCell> lockedCells = new ArrayList<GridCell>();
	// Current BFS stack. Function: Add new node to the stack, pop the first node in stack and expand etc.
	private Queue<Node> BFS_stack = new LinkedList<Node>();
	// Keep track of all gridcells that the robot has been visited, to prevent cycle
	private List<GridCell> node_visited = new ArrayList<GridCell>();
	
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
	public Entry<IGridRobot, Direction> nextStep(IWarehouse warehouse, Map<IGridRobot, GridCell> robot2dest) throws WaitForOthersException, nextStepNotFoundException {
		this.house = warehouse;
		// First check if robot2dest is valid
		if (!valid(robot2dest)){
			throw new IllegalArgumentException();
		}
		// Find the first robot that is not on dest
		Iterator<Entry<IGridRobot, GridCell>> robot_iter = robot2dest.entrySet().iterator();
		Entry<IGridRobot, GridCell> entry = null;
		while (true){
			if (robot_iter.hasNext()){
				entry = robot_iter.next();
				// If this robot is not on dest, and this robot is not moving, then choose this
				if (!robotOnDest(entry) && !movingRobots.contains(entry.getKey()) && !idleRobots.contains(entry.getKey())){
					break;
				}
			}
			else{
				// Return done if there is no more moving robots
				if (movingRobots.size() == 0 && idleRobots.size() == 0){
					return null;
				}
				// Else, throw an exception saying we need to wait for other robots to move
				throw new WaitForOthersException();
			}
		}
		IGridRobot robot = entry.getKey();
		GridCell dest = entry.getValue();

		// Add root to the stack
		Node root = new Node(robot.getLocation(), null);
		BFS_stack.add(root);
		try {
			Direction direction = BFS(root, dest);
			Entry<IGridRobot, Direction> result = new AbstractMap.SimpleEntry<IGridRobot, Direction>(robot, direction);
			clean();
			return result;
		}catch (nextStepNotFoundException e){
			/* Normally we dont want to throw nextStepNotFoundException back to the caller unless there is absolutely no way we can solve this
			 * Before we give up, we want to check if other robots are done moving. Sometimes they are just blocking the path.
			 * Hopefully by throwing WaitForOthersException we can solve this issue.
			 */
			if (movingRobots.size() != 0){
				clean();
				throw new WaitForOthersException();
			}
			/*
			 * 
			 * If we are sitting at someone's dest, we assign a random direction so that the other
			 * robot can get to its dest
			 * If someone is sitting at our dest, we wait
			 */
			robot_iter = robot2dest.entrySet().iterator();
			while (robot_iter.hasNext()){
				entry = robot_iter.next();
				IGridRobot current_robot = entry.getKey();
				GridCell current_dest = entry.getValue();
				if (current_dest.equals(robot.getLocation())){
					Direction random_direction = generateRandomDirection(robot);
					// If random_direction is null, that means is robot cannot move at all
					if (random_direction == null){
						// Then we return nextStepNotFoundException
						clean();
						throw e;
					}
					Entry<IGridRobot, Direction> result = new AbstractMap.SimpleEntry<IGridRobot, Direction>(robot, random_direction);
					clean();
					return result;
				}
				
				if (dest.equals(current_robot.getLocation())){
					System.out.println("Someone is sitting at our dest");
					clean();
					idleRobots.add(robot);
					throw new WaitForOthersException();
				}
			}
			
			System.out.println("Robot "+ robot.toString() + " cannot find path at " + robot.getLocation().toString());
			throw e;
		}
	}

	public Direction BFS(Node root, GridCell dest) throws nextStepNotFoundException{
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
		if (!dest.equals(current_node.getLocation())){
			throw new nextStepNotFoundException();
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
		// If this cell is locked
		if (lockedCells.contains(cell)){
			return true;
		}
		for (Node n : BFS_stack){
			if(n.getLocation().equals(cell)){
				return true;
			}
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
	// Assign a valid direction for the robot
	// If no valid direction, then return null
	public Direction generateRandomDirection(IGridRobot robot){
		clean();
		Direction result;
		GridCell cell = robot.getLocation();
		int x= cell.x;
		int y = cell.y;
		if (!checkIllegalCell(GridCell.at(x, y+1))){
			return IGridRobot.Direction.NORTH;
		}
		else if(!checkIllegalCell(GridCell.at(x+1, y))){
			return IGridRobot.Direction.EAST;
		}
		else if(!checkIllegalCell(GridCell.at(x, y-1))){
			return IGridRobot.Direction.SOUTH;
		}
		else if(!checkIllegalCell(GridCell.at(x-1, y))){
			return IGridRobot.Direction.WEST;
		}
		return null;
		
	};
	// Check if robot is on destination already
	public boolean robotOnDest(Entry<IGridRobot, GridCell> entry){
		GridCell robot_cell = entry.getKey().getLocation();
		GridCell dest = entry.getValue();
		if (robot_cell.equals(dest)){
			return true;
		}
		return false;
	}
	// Check if given robot2dest is valid
	// Return false if two or more robots share the same destination, or the dest is not on the floorplan
	public boolean valid(Map<IGridRobot, GridCell> robot2dest){
		List<GridCell> valuesList = new ArrayList<GridCell>(robot2dest.values());
		Set<GridCell> valuesSet = new HashSet<GridCell>(robot2dest.values());
		if (valuesSet.size() != valuesList.size()){
			return false;
		}
		for (GridCell g: valuesList){
			if (!house.getFloorPlan().hasCell(g)){
				return false;
			}
		}
		return true;
	}
	// Given robot is heading to direction, we will lock up this robot, and the cell the robot is about to step to
	// So that if other threads come call for nextStep, pathPlanner will not considerr this robot and this cell
	public void lock(IGridRobot robot, Direction direction){
		movingRobots.add(robot);
		lockedCells.add(GridRobot.oneCellOver(robot.getLocation(), direction));
	}
	
	public void unlock(IGridRobot robot){
		movingRobots.remove(robot);
		lockedCells.remove(robot.getLocation());
		idleRobots = new ArrayList<IGridRobot>();
	}
	// Clean all the variables in this class. So that next time we will have an empty stacks
	public void clean(){
		BFS_stack = new LinkedList<Node>();
		node_visited = new ArrayList<GridCell>();
		idleRobots = new ArrayList<IGridRobot>();
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
	
