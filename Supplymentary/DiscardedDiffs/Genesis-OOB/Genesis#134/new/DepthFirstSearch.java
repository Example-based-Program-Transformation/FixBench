/**
 * Depth First search
 *
 * Searches a map
 * DFS is fun!
 */

import java.util.ArrayList;

import java.util.Map;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DepthFirstSearch
{
	private int [][] weights;
	//private boolean[][] marked;
	private int parent[][][];
	private ArrayList<ArrayList<Map<String, Object>>> board;
	private int count;
	private ArrayList<Map<String, Object>> snakes;

	//public DepthFirstSearch(Map<String, Object> board[][])
	public DepthFirstSearch(ArrayList<ArrayList<Map<String, Object>>> board, ArrayList<Map<String, Object>> snakes)
	{
		//max_depth = depth;
		//this.weights = new int[board.length][board[0].length];
		//this.parent = new int[board.length][board[0].length][2];
		this.weights = new int[board.size()][board.get(0).size()];
		this.parent = new int[board.size()][board.get(0).size()][2];
		this.board = board;
		this.snakes = snakes;
		for (int i = 0; i < board.size(); i++)
		{
			for (int j = 0; j < board.get(0).size(); j++)
			{
				this.weights[i][j] = Integer.MIN_VALUE;
				this.parent[i][j][0] = -1;
				this.parent[i][j][1] = -1;
			}
		}
	}


	public String dfs(int x, int y)
	{
		LinkedBlockingQueue nodes = new LinkedBlockingQueue();
		nodes.add(new Point(x, y,-1,-1,1));
		while (nodes.size() > 0)
		{
			Point node = (Point)nodes.poll();
			//Map<String, Object> n_content = board[node.x][node.y];
			// FIXME getVal
			//int weight = getVal(node.x,node.y) - node.depth;
			if (node.x < 0 || node.y < 0 || node.x > board.size() || node.y > board.get(0).size())
			{
				continue;
			}
			int weight = ValueClass.getVal(node.x, node.y, x, y, (Object)this.board) - node.depth;
			if (weight > weights[node.x][node.y]){
				this.weights[node.x][node.y] = weight;
				this.parent[node.x][node.y][0] = node.parx;
				this.parent[node.x][node.y][1] = node.pary;
				nodes.add(new Point(node.x+1,node.y,node.x,node.y,node.depth+1));
				nodes.add(new Point(node.x-1,node.y,node.x,node.y,node.depth+1));
				nodes.add(new Point(node.x,node.y+1,node.x,node.y,node.depth+1));
				nodes.add(new Point(node.x,node.y-1,node.x,node.y,node.depth+1));
			}
		}
		int max_val = Integer.MIN_VALUE;
		int max_x = Integer.MIN_VALUE;
		int max_y = Integer.MIN_VALUE;
		for (int i = 0; i < weights.length; i++)
		{
			for (int j = 0; j < weights[0].length; j++)
			{
				if (this.weights[i][j] > max_val)
				{
				       	max_val = this.weights[i][j];
					max_x = i;
					max_y = j;
				}
			}
		}

		int t_max_x;
		while (this.parent[max_x][max_y][0] != x
				|| parent[max_x][max_y][1] != y)
		{
			t_max_x = max_x;
			max_x = this.parent[t_max_x][max_y][0];
			max_y = this.parent[t_max_x][max_y][1];
		}

		if (max_x < x) return "left";
		else if (max_x > x) return "right";
		else if (max_y > y) return "down";
		else if (max_y < y) return "up"; // yes!
		else return "You are wrong!";

	}

	private class Point
	{
		public int x, y, parx, pary, depth;
		public Point(int x, int y, int parx, int pary, int depth)
		{
			this.x = x;
			this.y = y;
			this.parx = parx;
			this.pary = pary;
			this.depth = depth;
		}
	}
}
