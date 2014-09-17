package com.davfx.ninio.trash;

import java.io.File;

import com.davfx.ninio.util.DeepDirectoryIterable;
import com.google.common.base.Joiner;

public class TestDeepIterable {
	public static void main(String[] args) {
		System.out.println(Joiner.on(',').join(new DeepDirectoryIterable(new File("src"))));
	}
}
