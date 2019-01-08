package com.davfx.ninio.core.v4;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ByteArraysTest {
	// private static final Logger LOGGER = LoggerFactory.getLogger(ByteArraysTest.class);

	private static byte[] testBytes(int start, int l) {
		byte[] b = new byte[l];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) (start + i);
		}
		return b;
	}
	
	@Test
	public void testFlatten() throws Exception {
		ByteArray b = new ByteArray(new byte[][] {
			testBytes(0, 3),
			testBytes(3, 4),
			testBytes(7, 5)
		});
		Assertions.assertThat(ByteArrays.flattened(b)).isEqualTo(testBytes(0, 12));
	}

	@Test
	public void testCut() throws Exception {
		ByteArray b = new ByteArray(new byte[][] {
			testBytes(0, 3),
			testBytes(3, 4),
			testBytes(7, 5)
		});
		Assertions.assertThat(ByteArrays.cut(b, 0, 3)).isEqualTo(testBytes(0, 3));
		Assertions.assertThat(ByteArrays.cut(b, 0, 3) == b.bytes[0]).isTrue(); // Optimization
		Assertions.assertThat(ByteArrays.cut(b, 3, 4) == b.bytes[1]).isTrue(); // Optimization
		Assertions.assertThat(ByteArrays.cut(b, 1, 2)).isEqualTo(testBytes(1, 2));
		Assertions.assertThat(ByteArrays.cut(b, 1, 3)).isEqualTo(testBytes(1, 3));
		Assertions.assertThat(ByteArrays.cut(b, 0, 11)).isEqualTo(testBytes(0, 11));
		Assertions.assertThat(ByteArrays.cut(b, 1, 11)).isEqualTo(testBytes(1, 11));
		Assertions.assertThat(ByteArrays.cut(b, 3, 8)).isEqualTo(testBytes(3, 8));
		Assertions.assertThat(ByteArrays.cut(b, 3, 9)).isEqualTo(testBytes(3, 9));

		Assertions.assertThat(ByteArrays.cut(b, 0, 0)).isEqualTo(testBytes(0, 0));
		Assertions.assertThat(ByteArrays.cut(b, 1, 0)).isEqualTo(testBytes(1, 0));
		Assertions.assertThat(ByteArrays.cut(b, 3, 0)).isEqualTo(testBytes(3, 0));
}
}
