import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.http.GzipReader;
import com.davfx.ninio.http.HttpContentReceiver;

public class TestAdrien {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestAdrien.class);

	public static void main(String[] args) throws Exception {
		Failing fail = new Failing() {
			@Override
			public void failed(IOException e) {
				LOGGER.info("fail", e);
			}
		};
		final OutputStream out = new FileOutputStream(new File("a.json"));
		HttpContentReceiver wrappee = new HttpContentReceiver() {
			@Override
			public void received(ByteBuffer buffer) {
				try {
				out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
					out.flush();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOGGER.info("received " + ByteBufferUtils.toString(buffer));
			}

			@Override
			public void ended() {
				LOGGER.info("ended");
			}
		};

		RandomAccessFile aFile = new RandomAccessFile("src/test/resources/gzip-packet-chunked-1.raw", "r");
		FileChannel inChannel = aFile.getChannel();
		long fileSize = inChannel.size();
		ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
		inChannel.read(buffer);
		buffer.flip();

		RandomAccessFile aFile2 = new RandomAccessFile("src/test/resources/gzip-packet-chunked-2.raw", "r");
		FileChannel inChannel2 = aFile2.getChannel();
		long fileSize2 = inChannel2.size();
		ByteBuffer buffer2 = ByteBuffer.allocate((int) fileSize2);
		inChannel2.read(buffer2);
		buffer2.flip();
		
		ByteBuffer b;
		{
			File ff = new File("src/test/resources/packet-data.txt");
			 b = ByteBuffer.allocate((int) (ff.length() / 2));
			BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(ff)));
			while (true) {
				char[] c = new char[2];
				int l = r.read(c);
				if (l <= 0) {
					break;
				}
				byte v = (byte) Integer.parseInt(new String(c), 16);
				b.put(v);
			}
			b.flip();
		}
		ByteBuffer e;
		{
			File ff = new File("src/test/resources/packet-data2.txt");
			e = ByteBuffer.allocate((int) (ff.length() / 2));
			BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(ff)));
			while (true) {
				char[] c = new char[2];
				int l = r.read(c);
				if (l <= 0) {
					break;
				}
				byte v = (byte) Integer.parseInt(new String(c), 16);
				e.put(v);
			}
			e.flip();
		}
/*
		RandomAccessFile aFile2_ = new RandomAccessFile("src/test/resources/gzip-packet.raw", "r");
		FileChannel inChannel2_ = aFile2_.getChannel();
		long fileSize2_ = inChannel2_.size();
		ByteBuffer buffer2_ = ByteBuffer.allocate((int) fileSize2_);
		inChannel2_.read(buffer2_);
		buffer2_.flip();
*/
		ByteBuffer buffer3 = ByteBuffer.allocate(buffer.remaining() + buffer2.remaining());
		buffer3.put(buffer);
		buffer3.put(buffer2);
		buffer3.flip();

		GzipReader gzipReader = new GzipReader(fail, wrappee);
//		gzipReader.received(buffer);
//		gzipReader.received(buffer3);
//		gzipReader.received(buffer2_);
		gzipReader.received(b);
		gzipReader.received(e);
		gzipReader.ended();
	}
}
