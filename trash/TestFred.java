import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.dns.DnsClient;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.dns.DnsConnection;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpConnecter;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestAddress;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.UrlUtils;

public class TestFred {
	public static void main(String[] args) throws Exception {
		Ninio ninio = Ninio.create();
		
		String url = "https://147.75.68.197/dataservice/j_security_check";
		DnsConnecter dns = ninio.create(DnsClient.builder());
		dns.connect(new DnsConnection() {
			@Override
			public void closed() {
			}
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void connected(Address address) {
			}
		});
	
		final HttpConnecter client = ninio.create(HttpClient.builder().with(dns));
	
		UrlUtils.ParsedUrl parsedUrl = UrlUtils.parse(url);
		HttpRequest request = new HttpRequest(new HttpRequestAddress(parsedUrl.host, parsedUrl.port, parsedUrl.secure), HttpMethod.GET, parsedUrl.path, parsedUrl.headers);
	
		client.request()
			.build(request)
			.receive(new HttpReceiver() {
				@Override
				public void failed(IOException ioe) {
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
							System.out.println("Content received: " + b.toString());
						}
					};
				}
			})
		.finish();
	
		Thread.sleep(15000);
	}
}
