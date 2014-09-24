package com.davfx.ninio.util;

import java.io.File;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

public final class DeepDirectoryIterable implements Iterable<File> {
	private final File root;
	public DeepDirectoryIterable(File root) {
		this.root = root;
	}
	
	@Override
	public Iterator<File> iterator() {
		final Deque<List<File>> children = new LinkedList<>();
		File[] l = root.listFiles();
		if (l != null) {
			children.add(Lists.newArrayList(l));
		}
		
		return new Iterator<File>() {
			private File next;
			
			{
				getNext();
			}
			
			private void getNext() {
				if (children.isEmpty()) {
					next = null;
					return;
				}
				List<File> files = children.getFirst();
				next = files.remove(0);
				if (files.isEmpty()) {
					children.removeFirst();
				}
				if (next.isDirectory()) {
					File[] l = next.listFiles();
					if (l != null) {
						children.addFirst(Lists.newArrayList(l));
					}
				}
			}
			
			@Override
			public boolean hasNext() {
				return (next != null);
			}
			
			@Override
			public File next() {
				File f = next;
				getNext();
				return f;
			}
			
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
