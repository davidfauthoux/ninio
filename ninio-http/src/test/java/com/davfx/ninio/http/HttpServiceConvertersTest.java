package com.davfx.ninio.http;

import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.http.service.Annotated;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.ParameterConverter;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class HttpServiceConvertersTest {
	
	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithQueryParameterController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}

	@Path("/get")
	public static final class TestGetWithIntegerController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") Integer message) {
			return Http.ok().content("GET hello:" + (message + 666));
		}
	}
	@Test
	public void testGetWithInteger() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithIntegerController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=42")).isEqualTo("text/plain; charset=UTF-8/GET hello:" + (42 + 666) + "\n");
			}
		}
	}
	
	@Path("/get")
	public static final class TestGetWithIntController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") int message) {
			return Http.ok().content("GET hello:" + (message + 666));
		}
	}
	@Test
	public void testGetWithInt() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithIntController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=42")).isEqualTo("text/plain; charset=UTF-8/GET hello:" + (42 + 666) + "\n");
			}
		}
	}
	
	@Path("/get")
	public static final class TestGetWithAllController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(
				@QueryParameter("boolean") boolean _boolean,
				@QueryParameter("byte") byte _byte,
				@QueryParameter("short") short _short,
				@QueryParameter("int") int _int,
				@QueryParameter("long") long _long,
				@QueryParameter("float") float _float,
				@QueryParameter("double") double _double,
				@QueryParameter("char") char _char
			) {
			return Http.ok().content("GET hello:" + _boolean + " " + _byte + " " + _short + " " + _int + " " + _long + " " + _float + " " + _double + " " + _char);
		}
	}
	@Test
	public void testGetWithAll() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithAllController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?boolean=true&byte=1&short=2&int=3&long=4&float=5.5&double=6.6&char=c")).isEqualTo("text/plain; charset=UTF-8/GET hello:true 1 2 3 4 5.5 6.6 c\n");
			}
		}
	}

	private static final class A {
		private final String id;
		private A(String id) {
			this.id = id;
		}
		public static A of(String id) {
			return new A(id);
		}
		@Override
		public String toString() {
			return "_" + id + "_";
		}
	}
	private static final class AConverter implements ParameterConverter<A> {
		@Override
		public A of(String s) throws Exception {
			return A.of(s);
		}
	}
	@Path("/get")
	public static final class TestGetWithConverterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("p") A p) {
			return Http.ok().content("GET hello:" + p);
		}
	}
	@Test
	public void testGetWithConverter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.Visitor() {
				@Override
				public void visit(Annotated.Builder builder) {
					builder.parameters(A.class, new AConverter());
					builder.register(TestGetWithConverterController.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?p=aaa")).isEqualTo("text/plain; charset=UTF-8/GET hello:_aaa_\n");
			}
		}
	}
	@Test
	public void testGetWithClazzConverter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.Visitor() {
				@Override
				public void visit(Annotated.Builder builder) {
					builder.parameters(A.class);
					builder.register(TestGetWithConverterController.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?p=aaa")).isEqualTo("text/plain; charset=UTF-8/GET hello:_aaa_\n");
			}
		}
	}
	
	@Path("/get")
	public static final class TestGetWithListConverterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("p") List<A> p) {
			return Http.ok().content("GET hello:" + p);
		}
	}
	@Test
	public void testGetWithListConverter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.Visitor() {
				@Override
				public void visit(Annotated.Builder builder) {
					builder.parameters(new TypeToken<List<A>>() {
						private static final long serialVersionUID = 1L;
					}, new ParameterConverter<List<A>>() {
						@Override
						public List<A> of(String s) throws Exception {
							List<A> l = new LinkedList<>();
							for (JsonElement e : new JsonParser().parse(s).getAsJsonArray()) {
								l.add(A.of(e.getAsString()));
							}
							return l;
						}
					});
					builder.register(TestGetWithListConverterController.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?p=['a','a','a']")).isEqualTo("text/plain; charset=UTF-8/GET hello:[_a_, _a_, _a_]\n");
			}
		}
	}

	private static final class B {
		private final String id;
		private B(String id) {
			this.id = id;
		}
		public static B of(String id) {
			return new B(id);
		}
		@Override
		public String toString() {
			return "^" + id + "^";
		}
	}
	@Path("/get")
	public static final class TestGetWithTwoListConverterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("p") List<A> p, @QueryParameter("q") List<B> q) {
			return Http.ok().content("GET hello:" + p + q);
		}
	}
	@Test
	public void testGetWithTwoListConverter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.Visitor() {
				@Override
				public void visit(Annotated.Builder builder) {
					builder.parameters(new TypeToken<List<A>>() {
						private static final long serialVersionUID = 1L;
					}, new ParameterConverter<List<A>>() {
						@Override
						public List<A> of(String s) throws Exception {
							List<A> l = new LinkedList<>();
							for (JsonElement e : new JsonParser().parse(s).getAsJsonArray()) {
								l.add(A.of(e.getAsString()));
							}
							return l;
						}
					});
					builder.parameters(new TypeToken<List<B>>() {
						private static final long serialVersionUID = 1L;
					}, new ParameterConverter<List<B>>() {
						@Override
						public List<B> of(String s) throws Exception {
							List<B> l = new LinkedList<>();
							for (JsonElement e : new JsonParser().parse(s).getAsJsonArray()) {
								l.add(B.of(e.getAsString()));
							}
							return l;
						}
					});
					builder.register(TestGetWithTwoListConverterController.class);
				}
			})) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?p=['a','a','a']&q=['b']")).isEqualTo("text/plain; charset=UTF-8/GET hello:[_a_, _a_, _a_][^b^]\n");
			}
		}
	}

}
