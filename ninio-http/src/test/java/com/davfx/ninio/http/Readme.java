package com.davfx.ninio.http;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;

public final class Readme {

	@Path("/ech")
	public static final class EchoController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/o")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("ECHO " + message);
		}
	}
	
	public static void main(String[] args) throws Exception {
		int port = 8080;
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Queue(), new Address(Address.ANY, port))) {
			server.register(EchoController.class);

			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/o?message=helloworld");
			
			Thread.sleep(100000);
		}
	}

}
