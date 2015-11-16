package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;

public final class CutOnPromptReadyConnection implements ReadyConnection {
	private final ReadyConnection wrappee;
	
	private ByteBuffer currentPrompt;
	private List<ByteBuffer> previous = null;
	private final List<ByteBuffer> buffers = new LinkedList<>();

	public CutOnPromptReadyConnection(ReadyConnection wrappee) {
		this.wrappee = wrappee;
	}
	
	public void setPrompt(ByteBuffer prompt) {
		currentPrompt = prompt;
	}
	
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		wrappee.connected(write);
	}
	@Override
	public void close() {
		buffers.clear();
		wrappee.close();
	}
	@Override
	public void failed(IOException e) {
		buffers.clear();
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
		if (currentPrompt == null) {
			return;
		}
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
				buffers.add(buffer);
				return;
			}
			
			ByteBuffer startBuffer = ByteBuffer.wrap(buffer.array(), buffer.position(), position);
			buffer = ByteBuffer.wrap(buffer.array(), buffer.position() + position, buffer.remaining() - position);
			buffers.add(startBuffer);

			if (!buffers.isEmpty()) {
				int l = 0;
				for (ByteBuffer b : buffers) {
					l += b.remaining();
				}
				byte[] buf = new byte[l];
				int off = 0;
				for (ByteBuffer b : buffers) {
					int len = b.remaining();
					b.get(buf, off, len);
					off += len;
				}
	
				buffers.clear();
				wrappee.handle(address, ByteBuffer.wrap(buf));
			}

			previous = null;
		}
	}
}
