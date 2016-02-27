/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IRegion;

/**
 * @see IRegion
 */

@SuppressWarnings({ "rawtypes", "unchecked" })
public class Region implements IRegion {

	private static final class Node {
		private static Map<IJavaElement, Node> children = Collections.emptyMap();

		public Node() {
		}

		public void clearChildren() {
			children = Collections.emptyMap();
		}

		public Node createChildFor(IJavaElement element) {
			if (children.isEmpty()) {
				children = new HashMap<>();
			}

			Node child = children.get(element);

			if (child == null) {
				child = new Node();
				children.put(element, child);
			}

			return child;
		}

		public Node findChildFor(IJavaElement element) {
			return children.get(element);
		}

		public int countLeafNodes() {
			if (isEmpty()) {
				return 1;
			}

			int result = 0;
			for (Node next : children.values()) {
				result += next.countLeafNodes();
			}
			return result;
		}

		boolean isEmpty() {
			return children.isEmpty();
		}

		public int gatherLeaves(IJavaElement[] result, int i) {
			for (Map.Entry<IJavaElement, Node> next : children.entrySet()) {
				Node nextNode = next.getValue();
				if (nextNode.isEmpty()) {
					result[i++] = next.getKey();
				} else {
					i = nextNode.gatherLeaves(result, i);
				}
			}
			return i;
		}

		public void removeChild(IJavaElement currentElement) {
			children.remove(currentElement);
		}
	}

	private Node root = new Node();

	@Override
	public void add(IJavaElement element) {
		if (contains(element)) {
			return;
		}
		Node node = createNodeFor(element);
		node.clearChildren();
	}

	private Node createNodeFor(IJavaElement element) {
		if (element == null) {
			return this.root;
		}

		Node parentNode = createNodeFor(element.getParent());

		return parentNode.createChildFor(element);
	}

	@Override
	public boolean contains(IJavaElement element) {
		Node existingNode = findMostSpecificNodeFor(element);

		if (existingNode == this.root) {
			return false;
		}
		return existingNode.isEmpty();
	}

	private Node findMostSpecificNodeFor(IJavaElement element) {
		if (element == null) {
			return this.root;
		}

		Node parentNode = findMostSpecificNodeFor(element.getParent());
		Node child = parentNode.findChildFor(element);
		if (child == null) {
			return parentNode;
		}
		return child;
	}

	@Override
	public IJavaElement[] getElements() {
		int leaves = countLeafNodes();

		IJavaElement[] result = new IJavaElement[leaves];
		int insertions = this.root.gatherLeaves(result, 0);

		assert insertions == leaves;

		return result;
	}

	private int countLeafNodes() {
		if (this.root.isEmpty()) {
			return 0;
		}
		return this.root.countLeafNodes();
	}

	private Node findExactNode(IJavaElement element) {
		if (element == null) {
			return this.root;
		}

		Node parentNode = findExactNode(element.getParent());

		if (parentNode == null) {
			return null;
		}

		return parentNode.findChildFor(element);
	}

	@Override
	public boolean remove(IJavaElement element) {
		Node node = findExactNode(element);

		if (node == null) {
			return false;
		}
		node.clearChildren();
		boolean returnValue = node.isEmpty();

		List<Node> ancestors = new ArrayList<>();
		findPath(ancestors, element);

		IJavaElement currentElement = element;
		int idx = ancestors.size();

		while (--idx > 0 && currentElement != null) {
			Node current = ancestors.get(idx);
			Node parent = ancestors.get(idx - 1);

			if (current.isEmpty()) {
				parent.removeChild(currentElement);
			} else {
				break;
			}
			currentElement = currentElement.getParent();
		}

		return returnValue;
	}

	private void findPath(List<Node> ancestors, IJavaElement element) {
		if (element == null) {
			ancestors.add(this.root);
			return;
		}

		findPath(ancestors, element.getParent());

		Node last = ancestors.get(ancestors.size() - 1);
		Node next = last.findChildFor(element);
		if (next != null) {
			ancestors.add(next);
		}
	}
}
