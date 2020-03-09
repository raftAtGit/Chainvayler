package raft.chainvayler.compiler;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javassist.CtClass;

/** a node in class tree */
class Node {
	final CtClass clazz;
	final Map<String, Node> subClasses = new TreeMap<String, Node>();
	boolean compiled;
	boolean isRoot;
	Node parent;

	Node(CtClass clazz) {
		this.clazz = clazz;
	}

	void add(List<CtClass> hierarchy) {
		assert (hierarchy.get(0) == clazz);
		
		if (hierarchy.size() == 1)
			return;
		
		CtClass subClass = hierarchy.get(1);
		Node subNode = subClasses.get(subClass.getName());
		
		if (subNode == null) {
			subNode = new Node(subClass);
			subClasses.put(subClass.getName(), subNode);
		}
		
		subNode.parent = this;
		subNode.add(hierarchy.subList(1, hierarchy.size()));
	}
	
	Node findNode(String className) {
		if (clazz.getName().equals(className))
			return this;
		
		Node sub = subClasses.get(className);
		if (sub != null)
			return sub;
		
		for (Node node : subClasses.values()) {
			Node n = node.findNode(className);
			if (n != null)
				return n;
		}
		
		return null;
	}
	
	void print(PrintStream out, int indentation) {
		out.println(indent(indentation) + clazz.getName() + (isRoot ? " (*)" : ""));
//		out.println(indent(indentation) + clazz.getName() + (isRoot ? " (*)" : "") + ((parent == null) ? "" : " parent: " + parent.clazz.getName()));
		
		for (Node subNode : subClasses.values()) {
			subNode.print(out, indentation + 4);
		}
	}


	private String indent(int count) {
		String s = "";
		for (int i = 0; i < count; i++) {
			s += " ";
		}
		return s;
	}

	boolean isTopLevel() {
		return (parent == null);
	}
	
	Node getTopLevelNode() {
		Node topLevel = this;
		while (topLevel.parent != null) {
			topLevel = topLevel.parent;
		}
		return topLevel;
	}
}