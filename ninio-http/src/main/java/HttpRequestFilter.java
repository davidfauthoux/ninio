

import com.davfx.ninio.http.HttpRequest;

public interface HttpRequestFilter {
	boolean accept(HttpRequest request);
}
