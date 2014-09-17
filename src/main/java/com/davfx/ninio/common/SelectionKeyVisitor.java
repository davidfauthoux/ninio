package com.davfx.ninio.common;

import java.nio.channels.SelectionKey;

interface SelectionKeyVisitor {
	void visit(SelectionKey key);
}
