package nl.tudelft.ti2806.pl1.gui.contentpane;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JScrollPane;

import nl.tudelft.ti2806.pl1.gui.Event;
import nl.tudelft.ti2806.pl1.gui.ToolBar;
import nl.tudelft.ti2806.pl1.phylotree.BinaryTree;

import com.wordpress.tips4java.ScrollablePanel;

/**
 * @author Maarten
 *
 */
public class PhyloPanel extends JScrollPane implements ContentTab {

	/** The serial version UID. */
	private static final long serialVersionUID = -1936473122898892804L;

	/** The size of the nodes. */
	private static final int NODE_WIDTH = 100, NODE_HEIGHT = 25;

	/** The width of the edges connecting the nodes. */
	private static final float EDGE_WIDTH = 2;

	/** The number of decimal places to round the path lengths to. */
	private static final int DIST_ROUND_TO_N = 3;

	/** The color of a chosen/selected node. */
	public static final Color NODE_SELECTED_COLOR = Color.ORANGE;

	/** The color of an edge between two chosen/selected nodes. */
	public static final Color EDGE_BETWEEN_SELECTED_COLOR = Color.ORANGE;

	/** The color of a collapsed inner node. */
	public static final Color INNER_NODE_COLLAPSED = Color.RED;

	/** The color of a normal (not collapsed) inner node. */
	public static final Color INNER_NODE_NORMAL = Color.BLACK;

	/** The default color to use. */
	public static final Color DEFAULT_COLOR = Color.BLACK;

	/** The tree that this panel will show. */
	private BinaryTree tree;

	/**
	 * @return The loaded tree.
	 */
	public final BinaryTree getTree() {
		return tree;
	}

	/** The Newick formatted input string. */
	private String newickSource;

	/** The scroll panel. */
	private ScrollablePanel treePanel;

	/** The space in between the nodes. */
	private static final Insets INSETS = new Insets(3, 10, 0, 0);

	/**
	 * Initializes the panel.
	 */
	public PhyloPanel() {
		treePanel = new ScrollablePanel() {
			/** The serial version UID */
			private static final long serialVersionUID = 118748767146413611L;

			@Override
			protected final void paintComponent(final Graphics g) {
				super.paintComponent(g);
				if (tree != null) {
					drawLines(g, tree);
				}
			}
		};
		treePanel.setLayout(null);
		setViewportView(treePanel);
	}

	@Override
	public List<JComponent> getToolBarControls() {
		List<JComponent> ret = new ArrayList<JComponent>(2);
		ret.add(ToolBar.makeButton("Highlight selection", null, null, null));
		ret.add(ToolBar.makeButton("Filter selection", null, null, null));
		return ret;
	}

	/**
	 * Draws all the lines from given node and its children recursively.
	 * 
	 * @param g
	 *            The graphics object.
	 * @param node
	 *            The root of the tree to draw lines in.
	 */
	private void drawLines(final Graphics g, final BinaryTree node) {
		Graphics2D g2 = (Graphics2D) g;
		g2.setStroke(new BasicStroke(EDGE_WIDTH));
		if (!node.isCollapsed()) {
			List<BinaryTree> children = node.getChildren();
			int x = (int) node.getCenter().getX();
			int y = (int) node.getCenter().getY();
			for (BinaryTree child : children) {
				g2.setColor(getLineColor(node, child));
				int childX = (int) child.getCenter().getX();
				int childY = (int) child.getCenter().getY();
				if (childY == y) {
					g2.drawLine(x, y, childX, y);
					g2.drawString(child.getPathLengthN(DIST_ROUND_TO_N),
							(x + childX) / 2, childY);
				} else {
					g2.drawLine(x, y, x, childY);
					g2.drawLine(x, childY, childX, childY);
					g2.drawString(child.getPathLengthN(DIST_ROUND_TO_N), x,
							(y + childY) / 2);
				}
				g2.setColor(DEFAULT_COLOR);
				drawLines(g, child);
			}
		}
	}

	/**
	 * Determines the color to draw the connecting line between two connected
	 * nodes with.
	 * 
	 * @param parent
	 *            The parent node.
	 * @param child
	 *            The child node.
	 * @return The appropriate line color to connect the <code>parent</code> to
	 *         the <code>child</code> with.
	 */
	private Color getLineColor(final BinaryTree parent, final BinaryTree child) {
		if (parent.getChildren().contains(child) && parent.isChosen()
				&& child.isChosen()) {
			return EDGE_BETWEEN_SELECTED_COLOR;
		}
		return DEFAULT_COLOR;
	}

	/**
	 * Loads a newick file.
	 * 
	 * @param newick
	 *            The Newick file to load.
	 * @return true iff the tree was loaded successfully.
	 */
	public final boolean loadTree(final File newick) {
		boolean ret = true;
		try {
			this.newickSource = readIntoString(newick);
		} catch (IOException e) {
			ret = false;
			Event.statusBarError("File " + newick.getAbsolutePath()
					+ " could not be read.");
			e.printStackTrace();
		}
		this.tree = BinaryTree.parseNewick(newickSource, this);
		plotTree();
		return ret;
	}

	/**
	 * Plots the tree as a button.
	 */
	public final void plotTree() {
		removeTree();
		int treeWidth = tree.computePlacement(0, 0);
		int treeHeight = tree.height();
		// System.out.println(tree.toString());
		addNode(tree);
		treePanel.setPreferredSize(new Dimension((treeHeight + 1)
				* (NODE_WIDTH + INSETS.left) + INSETS.left, (treeWidth + 1)
				* (NODE_HEIGHT + INSETS.top) + INSETS.top));
		repaint();
		revalidate();
	}

	/**
	 * Empties the panel. All tree components will be deleted.
	 */
	public final void removeTree() {
		treePanel.removeAll();
	}

	/**
	 * Adds the current tree and all its children if it is not collapsed.
	 * 
	 * @param bintree
	 *            The current tree
	 */
	private void addNode(final BinaryTree bintree) {
		// treePanel.remove(tree);
		int x = (int) bintree.getGridCoordinates().getX();
		int y = (int) bintree.getGridCoordinates().getY();
		bintree.setBounds(x * (NODE_WIDTH + INSETS.left) + INSETS.left, y
				* (NODE_HEIGHT + INSETS.top) + INSETS.top, NODE_WIDTH,
				NODE_HEIGHT);
		bintree.setText(bintree.getID());
		treePanel.add(bintree);
		if (!bintree.isCollapsed()) {
			for (BinaryTree t : bintree.getChildren()) {
				addNode(t);
			}
		}
	}

	/**
	 * Copies the entire file content into a single String, and returns it.
	 * 
	 * @param file
	 *            The file to read.
	 * @return the file content as a continuous string
	 * @throws IOException
	 *             when something goes wrong xD
	 */
	public static String readIntoString(final File file) throws IOException {
		final StringBuffer buff = new StringBuffer();

		final BufferedReader in = new BufferedReader(new FileReader(file));
		String line;
		while ((line = in.readLine()) != null) {
			buff.append(line);
		}
		in.close();

		return buff.toString();
	}
}
