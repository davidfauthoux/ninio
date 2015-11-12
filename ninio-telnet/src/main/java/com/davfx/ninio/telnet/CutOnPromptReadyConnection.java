package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;

public final class CutOnPromptReadyConnection implements ReadyConnection {
	private final ByteBufferAllocator promptAllocator;
	private final ReadyConnection wrappee;
	
	private ByteBuffer currentPrompt;
	private List<ByteBuffer> previous = null;
	
	public CutOnPromptReadyConnection(ByteBufferAllocator promptAllocator, ReadyConnection wrappee) {
		this.promptAllocator = promptAllocator;
		this.wrappee = wrappee;
	}
	
	private void allocatePrompt() {
		currentPrompt = promptAllocator.allocate();
		previous = null;
	}
	
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		wrappee.connected(write);
		allocatePrompt();
	}
	@Override
	public void close() {
		wrappee.close();
	}
	@Override
	public void failed(IOException e) {
		wrappee.failed(e);
	}
	
	private static int find(ByteBuffer toFind, List<ByteBuffer> intoBuffers) {
		int total = 0;
		int lastLength = 0;
		for (ByteBuffer b : intoBuffers) {
			total += b.remaining();
			lastLength = b.remaining();
		}
		int totalExcludingLast = total - lastLength;
		total -= toFind.remaining() - 1;
		
		int intoBuffersIndex = 0;
		int backOffset = 0;
		for (int i = 0; i < total; i++) {
			while (true) {
				ByteBuffer first = intoBuffers.get(intoBuffersIndex);
				if ((i - backOffset) == first.remaining()) {
					intoBuffersIndex++;
					backOffset += first.remaining();
				} else {
					break;
				}
			}

			boolean diff = false;
			ByteBuffer intoBuffer = intoBuffers.get(intoBuffersIndex);
			int l = toFind.remaining();
			for (int j = 0; j < l; j++) {
				byte toFindByte = toFind.get(toFind.position() + j);
				
				byte intoBufferByte;
				while (true) {
					int k = i - backOffset + j;
					if (k == intoBuffer.remaining()) {
						intoBuffersIndex++;
						backOffset += intoBuffer.remaining();
						intoBuffer = intoBuffers.get(intoBuffersIndex);
					} else {
						intoBufferByte = intoBuffer.get(intoBuffer.position() + k);
						break;
					}
				}

				if (toFindByte != intoBufferByte) {
					diff = true;
					break;
				}
			}
			
			if (!diff) {
				return i + l - totalExcludingLast; // Position in last buffer
			}
		}
		
		return -1;
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		while (true) {
			if (previous == null) {
				previous = new ArrayList<>();
			}
			previous.add(buffer.duplicate());
			int position = find(currentPrompt, previous);
			
			int lengthToKeep = currentPrompt.remaining() - 1;
			int index = previous.size() - 1;
			List<ByteBuffer> newPrevious = new ArrayList<>();
			while ((lengthToKeep > 0) && (index >= 0)) {
				ByteBuffer b = previous.get(index);
				if (b.remaining() < lengthToKeep) {
					newPrevious.add(0, b);
					lengthToKeep -= b.remaining();
				} else {
					newPrevious.add(0, ByteBuffer.wrap(b.array(), b.position() + b.remaining() - lengthToKeep, lengthToKeep));
					break;
				}
				
				index--;
			}
			previous = newPrevious;
			
			if (position < 0) {
				wrappee.handle(address, buffer);
				return;
			}
			
			ByteBuffer startBuffer = ByteBuffer.wrap(buffer.array(), buffer.position(), position);
			buffer = ByteBuffer.wrap(buffer.array(), buffer.position() + position, buffer.remaining() - position);
			wrappee.handle(address, startBuffer);
			allocatePrompt();
		}
	}
}
