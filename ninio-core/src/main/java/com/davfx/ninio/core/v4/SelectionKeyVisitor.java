package com.davfx.ninio.core.v4;

import java.nio.channels.SelectionKey;

interface SelectionKeyVisitor {
	void visit(SelectionKey key);
}
