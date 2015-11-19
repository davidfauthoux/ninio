package com.davfx.ninio.http;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.controllers.ImageToJpegConverter;
import com.davfx.util.Wait;

public final class ReadmeWithImageToJpegConverterHttpService {

	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		int port = 8080;
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Queue(), new Address(Address.ANY, port))) {
			server.register(ImageToJpegConverter.class);

			String url = "http://www.journaldugeek.com/wp-content/blogs.dir/1/files/2015/10/delorean-back-to-the-future.jpg";
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/image.convert?url=" + UrlUtils.encode(url) + "&quality=1&width=100&height=100");
			wait.waitFor();
		}
	}

}
