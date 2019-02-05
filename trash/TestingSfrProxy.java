package com.davfx.ninio.http;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.SecureSocketBuilder;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpSocket.Builder;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.dns.DnsClient;
import com.davfx.ninio.dns.DnsConnecter;

public class TestingSfrProxy {
	
	public static void main(String[] args) throws Exception {
		final double TIMEOUT = 30d;
		final int LIMIT = 10; // Max number of concurrent HTTP requests

		UrlUtils.ParsedUrl parsedUrl = UrlUtils.parse("http://10.154.129.123:3128");

		final Address proxyAddress = new Address(InetAddress.getByName(parsedUrl.host).getAddress(), parsedUrl.port);

		try (Ninio ninio = Ninio.create()) { // Should always be created globally
			final Limit limit = new Limit();
			try (Timeout timeout = new Timeout()) {
				try (DnsConnecter dns = ninio.create(DnsClient.builder()); HttpConnecter client = ninio.create(HttpClient.builder().with(dns)
						
						.withSecure(new Builder() {
							
							private Address httpServerAddress = null;
							private final Builder builder = new SecureSocketBuilder(TcpSocket.builder());
							
							@Override
							public Connecter create(NinioProvider ninioProvider) {
								final Connecter connecter = builder.create(ninioProvider);
								connecter.send(httpServerAddress, ByteBufferUtils.toByteBuffer(""
										+ "CONNECT sso.sfr.com:443 HTTP/1.1\n"
										+ "Proxy-Authorization: Basic Zmxvd3RlYW06Y0B2aWFSMjAxMw==\n"
										+ "\n"), new Nop());
								return new Connecter() {
									
									@Override
									public void send(Address address, ByteBuffer buffer, SendCallback callback) {
										connecter.send(address, buffer, callback);
									}
									
									@Override
									public void close() {
										connecter.close();
									}
									
									@Override
									public void connect(Connection callback) {
										connecter.connect(callback);
									}
								};
							}
							
							@Override
							public Builder with(ByteBufferAllocator byteBufferAllocator) {
								builder.with(byteBufferAllocator);
								return this;
							}
							
							@Override
							public Builder to(Address connectAddress) {
								httpServerAddress = connectAddress;
								builder.to(proxyAddress);
								return this;
							}
							
							@Override
							public Builder bind(Address bindAddress) {
								builder.bind(bindAddress);
								return this;
							}
						})
						
					)) {

					HttpRequest request = new HttpRequest(new HttpRequestAddress(parsedUrl.host, parsedUrl.port, parsedUrl.secure), HttpMethod.POST, parsedUrl.path, parsedUrl.headers);

					HttpRequestBuilder b = HttpTimeout.wrap(timeout, TIMEOUT, HttpLimit.wrap(limit, LIMIT, client.request()));
					HttpContentSender s = b.build(request);
					b.receive(new HttpReceiver() {
							@Override
							public void failed(IOException e) {
								e.printStackTrace();
							}
							@Override
							public HttpContentReceiver received(HttpResponse response) {
								System.out.println("Response = " + response);
								return new HttpContentReceiver() {
									private final InMemoryBuffers b = new InMemoryBuffers();
									@Override
									public void received(ByteBuffer buffer) {
										b.add(buffer);
									}
									@Override
									public void ended() {
										System.out.println("Content = " + b.toString());
									}
								};
							}
						});
					s.finish();
					
					Thread.sleep(2000);
				}
			}
		}
	}

}
