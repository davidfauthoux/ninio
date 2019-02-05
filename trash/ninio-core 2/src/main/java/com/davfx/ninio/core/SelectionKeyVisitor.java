package com.davfx.ninio.core;

import java.nio.channels.SelectionKey;

interface SelectionKeyVisitor {
	void visit(SelectionKey key);
}
