package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.google.common.base.Charsets;

public class CutOnPromptReadyConnectionTest {

	private List<String> test(List<String> content, String prompt) {
		final List<String> result = new LinkedList<>();
		CuttingByteBufferHandler c = new CuttingByteBufferHandler(0, new FailableCloseableByteBufferHandler() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void close() {
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				result.add(s);
			}
		});
		
		c.setPrompt(ByteBuffer.wrap(prompt.getBytes(Charsets.UTF_8)));
		
		for (String contentString : content) {
			c.handle(null, ByteBuffer.wrap(contentString.getBytes(Charsets.UTF_8)));
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
