package com.davfx.ninio.http.util.controllers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.Http.InMemoryHandler;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.InMemoryBuffers;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;

@Path("/image.convert")
public final class ImageToJpegConverter implements HttpController {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ImageToJpegConverter.class);
	
	public ImageToJpegConverter() {
	}

	@Route(method = HttpMethod.GET)
	public Http convert(final @QueryParameter("url") String url, final @QueryParameter("quality") float quality, final @QueryParameter("width") @DefaultValue("0") int width, final @QueryParameter("height") @DefaultValue("0") int height) {
		LOGGER.debug("Converting: {}", url);

		return Http.ok().async(new HttpAsync() {
			@Override
			public void produce(final HttpAsyncOutput output) {
				new com.davfx.ninio.http.Http().get(url, new InMemoryHandler() {
					@Override
					public void failed(IOException e) {
						output.failed(e);
					}
					@Override
					public void handle(HttpResponse response, InMemoryBuffers content) {
						byte[] converted;
						try {
							BufferedImage image;
							try (InputStream in = new ByteArrayInputStream(content.toByteArray())) {
								image = ImageIO.read(in);
							}
							
							if ((width > 0) || (height > 0)) {
								int w = width;
								int h = height;
								if (w <= 0) {
									w = image.getWidth() * h / image.getHeight();
								} else if (h <= 0) {
									h = image.getHeight() * w / image.getWidth();
								}
								
								LOGGER.debug("{}x{} -> {}x{}", image.getWidth(), image.getHeight(), w, h);
								
								BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
								Graphics2D g = scaled.createGraphics();
								try {
								    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
								    g.drawImage(image, 0, 0, w, h, 0, 0, image.getWidth(), image.getHeight(), null);
								} finally {
									g.dispose();
								}
								
								image = scaled;
							}
							
							ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
							try {
								ImageWriteParam writerParameters = writer.getDefaultWriteParam();
								writerParameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
								writerParameters.setCompressionQuality(quality);

								ByteArrayOutputStream out = new ByteArrayOutputStream();
								try {
									writer.setOutput(ImageIO.createImageOutputStream(out));
									writer.write(null, new IIOImage(image, null, null), writerParameters);
								} finally {
									out.close();
								}
								converted = out.toByteArray();
							} finally {
								writer.dispose();
							}
						} catch (IOException ioe) {
							LOGGER.error("Could not convert image: {}", ioe);
							output.failed(ioe);
							return;
						}

						output.ok().contentLength(converted.length).contentType(HttpHeaderValue.simple("image/jpg")).produce(ByteBuffer.wrap(converted)).close();
					}
				});
			}
		});
	}
}
