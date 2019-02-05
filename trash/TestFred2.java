import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.util.HttpClient;
import com.davfx.ninio.util.Wait;
import com.google.gson.JsonParser;

public class TestFred2 {
public static void main(String[] args) throws Exception {
	while (true) {
	HttpClient client = new HttpClient();
	StringBuilder b = new StringBuilder();
	Wait w = new Wait();
	client.request().url(
			"https://124.252.253.37:6200/nuage_dpi_probestats/_search?scroll=1m&size=10000"
			).receive(new HttpReceiver() {
		
		@Override
		public void failed(IOException e) {
			e.printStackTrace();
		}
		
		@Override
		public HttpContentReceiver received(HttpResponse response) {
			return new HttpContentReceiver() {
				
				@Override
				public void received(ByteBuffer buffer) {
//					System.out.println("Received " + ByteBufferUtils.toString(buffer));
					b.append(ByteBufferUtils.toString(buffer));
				}
				
				@Override
				public void ended() {
					w.run();
				}
			};
		}
	})
	.header("User-Agent", "ninio")
	.header("Accept", "*/*")
	.header("Accept-Encoding", "gzip")
	//.header("Content-Length", "155")
	.header("Content-Type", "application/json")
	//.header("Accept", "application/json")
	//.header("Content-Type", "application/x-www-form-urlencoded")
	//.header("Cache-Control", "no-cache")
	//.post().send(ByteBufferUtils.toByteBuffer("j_username=admin&j_password=admin&submit=Log%2BIn\n\n"), new Nop()).finish();;
	.post().send(ByteBufferUtils.toByteBuffer("{"
			//+ "\"query\":{\"bool\":{\"must\":{\"range\":{\"timestamp\":{\"format\":\"epoch_millis\",\"gte\":1528848000000,\"lt\":1529107200000}}}}},"
			+ "\"sort\":[{\"timestamp\":{\"order\":\"asc\"}}]}"), new Nop()).finish();
	//.get();

	w.waitFor();
	
	String scrollId = new JsonParser().parse(b.toString()).getAsJsonObject().get("_scroll_id").getAsString();
	System.out.println("SCROLL ID = " + scrollId);
	
	for (int i = 0; i < 60; i++) {
		ByteBuffer bbb = ByteBufferUtils.toByteBuffer(
				"{\"scroll\":\"1m\",\"scroll_id\":\""
				+ scrollId
				+ "\"}"
			);
		Wait ww = new Wait();
		StringBuilder bb = new StringBuilder();
		client.request().url(
				"https://124.252.253.37:6200/_search/scroll"
				).receive(new HttpReceiver() {
			
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public HttpContentReceiver received(HttpResponse response) {
				System.out.println(response.headers);
				//System.out.println(response.headers.get("content-encoding"));
				return new HttpContentReceiver() {
					
					@Override
					public void received(ByteBuffer buffer) {
						//System.out.println("Received " + ByteBufferUtils.toString(buffer));
						bb.append(ByteBufferUtils.toString(buffer));
					}
					
					@Override
					public void ended() {
						ww.run();
					}
				};
			}
		})
		.header("User-Agent", "ninio")
		.header("Accept", "*/*")
		.header("Accept-Encoding", "gzip")
		.header("Content-Length", "" + bbb.remaining())
		.header("Content-Type", "application/json")
		//.header("Accept", "application/json")
		//.header("Content-Type", "application/x-www-form-urlencoded")
		//.header("Cache-Control", "no-cache")
		//.post().send(ByteBufferUtils.toByteBuffer("j_username=admin&j_password=admin&submit=Log%2BIn\n\n"), new Nop()).finish();;
		.post().send(bbb, new Nop()).finish();
		//.get();
		ww.waitFor();
		new JsonParser().parse(bb.toString());
		System.out.println("OK " + i);
	}
	
	Thread.sleep(1000);
	}
}
}
