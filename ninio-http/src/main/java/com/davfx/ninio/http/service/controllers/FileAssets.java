package com.davfx.ninio.http.service.controllers;

import java.io.File;

import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.service.FileHandler;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.HttpServiceRequest;
import com.davfx.ninio.http.service.annotations.Route;

public final class FileAssets implements HttpController {
	
	private final FileHandler handler;
	
	public FileAssets(File dir, String index) {
		handler = new FileHandler(dir, index);
	}
	
	@Route(method = HttpMethod.GET)
	public Http serve(HttpServiceRequest request) throws Exception {
		return handler.handle(request.path);
	}
}
