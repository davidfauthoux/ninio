

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.util.HttpClient;
import com.google.common.base.Charsets;

public class LoopTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(LoopTest.class);

	public static void main(String[] args) throws Exception {
		String url = System.getProperty("url", "http://10.154.129.243/auth/internal/login");
		String host = System.getProperty("host", "10.154.129.243");
		String post = System.getProperty("post", "{\"username\":\"admin\",\"password\":\"living@object\"}");
		double loopTime = Double.parseDouble(System.getProperty("loop", "10.0"));
		try (HttpClient client = new HttpClient()) {
			while (true) {
				LOGGER.info("Requesting: url={}, post={}", url, post);
				
				HttpClient.Request r = client.request();
				r.url(url).receive(new HttpReceiver() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
					
					@Override
					public HttpContentReceiver received(HttpResponse response) {
						return new HttpContentReceiver() {
							private final InMemoryBuffers b = new InMemoryBuffers();
							@Override
							public void received(ByteBuffer buffer) {
								b.add(buffer);
							}
							@Override
							public void ended() {
								String content = b.toString(Charsets.UTF_8);
								LOGGER.info("Content received: {}", content);
							}
						};
					}
				});
				r.header("Content-Type", "application/json");
				r.header("Host", host);
				if (post.isEmpty()) {
					r.get();
				} else {
					LOGGER.info("Posting: {}", post);
					r.post().send(ByteBufferUtils.toByteBuffer(post), new Nop()).finish();
				}
				
				LOGGER.info("Waiting {} seconds", loopTime);
				Thread.sleep((long) (loopTime * 1000d));
			}
		}
	}
}
