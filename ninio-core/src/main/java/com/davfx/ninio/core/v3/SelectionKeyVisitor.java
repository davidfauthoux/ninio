package com.davfx.ninio.core.v3;

import java.nio.channels.SelectionKey;

interface SelectionKeyVisitor {
	void visit(SelectionKey key);
}
