package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Charsets;

public class CutOnPromptReadyConnectionTest {

	private List<String> test(List<String> content, List<String> prompts) {
		final Iterator<String> promptsIterator = prompts.iterator();
		final List<ByteBuffer> buffers = new LinkedList<>();
		final List<String> result = new LinkedList<>();
		ReadyConnection c = new CutOnPromptReadyConnection(new ByteBufferAllocator() {
			@Override
			public ByteBuffer allocate() {
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
					
					String s = new String(buf, Charsets.UTF_8);
					result.add(s);
					
					buffers.clear();
				}
				
				String nextPrompt = promptsIterator.next();
				return ByteBuffer.wrap(nextPrompt.getBytes(Charsets.UTF_8));
			}
		}, new ReadyConnection() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void close() {
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				buffers.add(buffer);
			}
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
			}
		});
		
		c.connected(null);
		for (String contentString : content) {
			c.handle(null, ByteBuffer.wrap(contentString.getBytes(Charsets.UTF_8)));
		}
		return result;
	}

	@Test
	public void testSingleEnding() {
		List<String> r = test(Arrays.asList("012$>345$>6"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testSingleNoEnding() {
		List<String> r = test(Arrays.asList("012$>345$>"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testCutJustBeforePrompt() {
		List<String> r = test(Arrays.asList("012", "$>345$>6"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testSingleCharCuts() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", ">", "345$>6"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}
	
	@Test
	public void testSingleCharCutsPromptStuckToNext() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", ">345$>6"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testWithEmptyStrings() {
		List<String> r = test(Arrays.asList("0", "1", "", "2", "$", "", ">", "", "345$>6"), Arrays.asList("$>", "$>", "$>"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}
	
	@Test
	public void testWithSingleCharPrompt() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", "345$6$#"), Arrays.asList("$", "$", "$", "#", "#"));
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$", "345$", "6$", "#"));
	}
	

}
