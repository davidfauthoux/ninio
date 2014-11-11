package com.davfx.ninio.http.util;

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

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServerHandler;

public class OnTheFlyImageToJpegConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(OnTheFlyImageToJpegConverter.class);
	
	private final SimpleHttpClient client;
	
	public OnTheFlyImageToJpegConverter(HttpClientConfigurator clientConfigurator) {
		client = new SimpleHttpClient(clientConfigurator);
	}
	
	public HttpServerHandler create() {
		return new HttpServerHandler() {
			private HttpRequest request;
			
			@Override
			public void failed(IOException e) {
				close();
			}
			
			@Override
			public void close() {
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
			}
			
			@Override
			public void ready(final Write write) {
				final Parameters params = new HttpQuery(request.getPath()).getParameters();
				final String url = params.getValue("url");
				
				LOGGER.debug("Converting: {}", url);

				final String qualityAsString = params.getValue("quality");
				float q;
				if (qualityAsString == null) {
					q = 1f;
				} else {
					try {
						q = Float.parseFloat(qualityAsString);
					} catch (NumberFormatException nfe) {
						LOGGER.error("Invalid quality: {}", qualityAsString);
						q = 1f;
					}
				}
				final float quality = q;

				final String widthAsString = params.getValue("width");
				int w;
				if (widthAsString == null) {
					w = -1;
				} else {
					try {
						w = Integer.parseInt(widthAsString);
					} catch (NumberFormatException nfe) {
						LOGGER.error("Invalid width: {}", widthAsString);
						w = -1;
					}
				}
				final int width = w;

				final String heightAsString = params.getValue("height");
				int h;
				if (heightAsString == null) {
					h = -1;
				} else {
					try {
						h = Integer.parseInt(heightAsString);
					} catch (NumberFormatException nfe) {
						LOGGER.error("Invalid width: {}", heightAsString);
						h = -1;
					}
				}
				final int height = h;
				
				/*
				final String sizeAsString = params.getValue("size");
				int s;
				if (sizeAsString == null) {
					s = 0;
				} else {
					try {
						s = Integer.parseInt(sizeAsString);
					} catch (NumberFormatException nfe) {
						LOGGER.error("Invalid size: {}", sizeAsString);
						s = 0;
					}
				}
				final int size = s;
				*/

				client.withUrl(url).send(new SimpleHttpClientHandler() {
					@Override
					public void handle(int status, String reason, Parameters headers, InMemoryPost body) {
						if (status != Http.Status.OK) {
							HttpResponse r = new HttpResponse(status, reason);
							write.write(r);
							write.close();
							return;
						}
						
						byte[] converted;
						try {
							BufferedImage image;
							try (InputStream in = new ByteArrayInputStream(body.toByteArray())) {
								image = ImageIO.read(in);
							}
							
							if ((width >= 0) || (height >= 0)) {
								int w = width;
								int h = height;
								if (w < 0) {
									w = image.getWidth() * h / image.getHeight();
								} else if (h < 0) {
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
							
							/*
							if (size > 0) {
								image = Scalr.resize(image, size);
							}
							*/

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
							LOGGER.error("Could not convert image: " + url, ioe);
							HttpResponse r = new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR);
							write.write(r);
							return;
						}

						HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
						// r.getHeaders().put("Cache-Control", "private, max-age=0, no-cache");
						r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(converted.length));
						r.getHeaders().put(Http.CONTENT_TYPE, "image/jpg");
						write.write(r);
						write.handle(null, ByteBuffer.wrap(converted));
						write.close();
					}
				});
			}
			
			@Override
			public void handle(HttpRequest request) {
				this.request = request;
			}
		};
	}
}
