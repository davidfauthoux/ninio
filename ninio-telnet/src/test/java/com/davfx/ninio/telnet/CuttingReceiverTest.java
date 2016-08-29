package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.InMemoryBuffers;

public class CuttingReceiverTest {
	private List<String> test(List<String> content, String prompt) {
		final List<String> result = new LinkedList<>();
		CuttingReceiver c = new CuttingReceiver(0, ByteBufferUtils.toByteBuffer(prompt), new Connection() {
			private InMemoryBuffers buffers = new InMemoryBuffers();
			
			@Override
			public void received(Address address, ByteBuffer buffer) {
				if (buffer == null) {
					result.add(buffers.toString());
					buffers = new InMemoryBuffers();
				} else {
					buffers.add(buffer);
				}
			}
			
			@Override
			public void failed(IOException ioe) {
			}
			
			@Override
			public void connected(Address address) {
			}
			
			@Override
			public void closed() {
			}
		});
		
		for (String contentString : content) {
			c.received(null, ByteBufferUtils.toByteBuffer(contentString));
		}
		
		return result;
	}

	@Test
	public void testSingleEnding() {
		List<String> r = test(Arrays.asList("012$>345$>6"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testSingleNoEnding() {
		List<String> r = test(Arrays.asList("012$>345$>"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testCutJustBeforePrompt() {
		List<String> r = test(Arrays.asList("012", "$>345$>6"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testSingleCharCuts() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", ">", "345$>6"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}
	
	@Test
	public void testSingleCharCutsPromptStuckToNext() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", ">345$>6"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}

	@Test
	public void testWithEmptyStrings() {
		List<String> r = test(Arrays.asList("0", "1", "", "2", "$", "", ">", "", "345$>6"), "$>");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$>", "345$>"));
	}
	
	@Test
	public void testWithSingleCharPrompt() {
		List<String> r = test(Arrays.asList("0", "1", "2", "$", "345$6$$"), "$");
		Assertions.assertThat(r).isEqualTo(Arrays.asList("012$", "345$", "6$", "$"));
	}
	

}
