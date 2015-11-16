package com.davfx.ninio.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.HttpPathParameter;
import com.davfx.ninio.http.util.HttpQueryParameter;
import com.davfx.ninio.http.util.HttpRoute;
import com.davfx.ninio.http.util.HttpServiceResult;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.reflect.ClassPath;

public final class ReadmeWithAnnotatedHttpService {

	/*
	@HttpController()
	public static final class VerySimpleTestController {
		@HttpRoute()
		public void echo(@HttpQueryParameter("message") String message, HttpServiceResult result) {
			result.success(HttpContentType.plainText(), " " + message);
		}
	}
	*/

	@HttpController("/a")
	public static final class TestController {
		@HttpRoute("/echo")
		public void echo(@HttpQueryParameter("message") String message, HttpRequest request, InputStream post, HttpServiceResult result) {
			result.success("a/echo " + message);
		}
	}
	
	@HttpController("/b")
	public static final class SimpleTestController {
		@HttpRoute()
		public void echo(@HttpQueryParameter("message") String message, HttpServiceResult result) {
			result.success("b " + message);
		}
	}

	@HttpController("/e")
	public static final class SimpleEchoHelloWorldController {
		@HttpRoute("/{message}/{to}")
		public void echo(@HttpPathParameter("message") String message, @HttpPathParameter("to") String to, HttpServiceResult result) {
			result.success(message + " " + to);
		}
	}

	@HttpController("/ech")
	public static final class SimpleHelloWorldController {
		@HttpRoute("/{message}")
		public void echo(@HttpPathParameter("message") String message, @HttpQueryParameter("to") String to, HttpServiceResult result) {
			result.success(message + " " + to);
		}
	}

	@HttpController("/echo")
	public static final class EchoHelloWorldController {
		@HttpRoute("/{message}/{to}")
		public void echo(@HttpPathParameter("message") String message, @HttpPathParameter("to") String to, @HttpQueryParameter("n") String n, HttpServiceResult result) throws IOException {
			int nn = Integer.parseInt(n);
			try (OutputStream out = result.contentType(HttpContentType.plainText(Charsets.UTF_8)).success()) {
				for (int i = 0; i < nn; i++) {
					out.write((message + " " + to + "\n").getBytes(Charsets.UTF_8));
				}
			}
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		int port = 8080;
		try (AnnotatedHttpService server = new AnnotatedHttpService()) {
			ClassPath classPath = ClassPath.from(ReadmeWithAnnotatedHttpService.class.getClassLoader());
			
			for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
				Class<?> clazz;
				try {
					clazz = classInfo.load();
				} catch (LinkageError e) {
					continue;
				}
				server.register(clazz);
			}

			server.start(port);

			// System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/a/echo?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/b?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/e/Hello/World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/Hello?to=World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/hello/world?n=100");
			wait.waitFor();
		}
	}

}
