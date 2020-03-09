package raft.chainvayler.compiler;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javassist.CtClass;

// TODO make this and node inner classes of compiler again
class Tree {
	
	final Map<String, Node> roots = new TreeMap<String, Node>();
	
	List<CtClass> add(CtClass clazz) throws Exception {
		List<CtClass> hierarchy = Compiler.getChainedHierarchy(clazz);
		
		CtClass mostSuper = hierarchy.get(0);
		
		Node rootNode = roots.get(mostSuper.getName());
		if (rootNode == null) {
			rootNode = new Node(mostSuper);
			roots.put(mostSuper.getName(), rootNode);
		}
		
		rootNode.add(hierarchy);
		return hierarchy;
	}
	
	Node findNode(String className) {
		Node root = roots.get(className);
		if (root != null)
			return root;
		
		for (Node node : roots.values()) {
			Node n = node.findNode(className);
			if (n != null)
				return n;
		}
		
		return null;
	}

	void print(PrintStream out) {
		for (Node node : roots.values()) {
			node.print(out, 0);
		}
	}
	

}