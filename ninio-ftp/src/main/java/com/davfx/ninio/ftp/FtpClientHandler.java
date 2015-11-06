package com.davfx.ninio.ftp;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;

public interface FtpClientHandler extends Closeable, Failable {
	void authenticationFailed();
	
	interface Callback extends Closeable {
		interface ListCallback extends Failable {
			void handle(Iterable<String> content);
			void doesNotExist(String path);
		}
		interface DownloadCallback extends FailableCloseableByteBufferHandler {
			void doesNotExist(String path);
		}
		void list(String path, ListCallback callback);
		void download(String path, DownloadCallback handler);
	}
	void launched(Callback callback);
}
