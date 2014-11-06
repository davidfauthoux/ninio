package com.davfx.ninio.ftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReady;
import com.davfx.ninio.common.SocketReadyFactory;
import com.google.common.base.Splitter;

public final class FtpClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(FtpClient.class);

	public static final int DEFAULT_PORT = 21;

	private Queue queue = null;
	private String login = "user";
	private String password = "pass";
	private Address address = new Address("localhost", DEFAULT_PORT);
	private String host = null;
	private int port = -1;

	private ReadyFactory readyFactory = new SocketReadyFactory();

	public FtpClient() {
	}
	
	public FtpClient withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	public FtpClient withLogin(String login) {
		this.login = login;
		return this;
	}
	public FtpClient withPassword(String password) {
		this.password = password;
		return this;
	}
	
	public FtpClient withHost(String host) {
		this.host = host;
		return this;
	}
	public FtpClient withPort(int port) {
		this.port = port;
		return this;
	}
	public FtpClient withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public FtpClient override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public void connect(final FtpClientHandler clientHandler) {
		final Queue q;
		final boolean shouldCloseQueue;
		if (queue == null) {
			try {
				q = new Queue();
			} catch (IOException e) {
				clientHandler.failed(e);
				return;
			}
			shouldCloseQueue = true;
		} else {
			q = queue;
			shouldCloseQueue = false;
		}

		final Address a;
		if (host != null) {
			if (port < 0) {
				a = new Address(host, address.getPort());
			} else {
				a = new Address(host, port);
			}
		} else {
			a = address;
		}
		
		q.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create(q);
				ready.connect(a, new ReadyConnection() {
					private FtpResponseReader reader = null;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						reader.handle(address, buffer);
					}
					
					@Override
					public void failed(IOException e) {
						if (shouldCloseQueue) {
							q.close();
						}
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						reader = new FtpResponseReader(q, login, password, clientHandler, write);
						clientHandler.launched(new FtpClientHandler.Callback() {
							@Override
							public void close() {
								reader.close();
								if (shouldCloseQueue) {
									q.close();
								}
							}
							@Override
							public void list(String path, ListCallback callback) {
								reader.list(path, callback);
							}
							@Override
							public void download(String path, DownloadCallback handler) {
								reader.download(path, handler);
							}
						});
					}
					
					@Override
					public void close() {
						if (shouldCloseQueue) {
							q.close();
						}
						reader.close();
					}
				});
			}
		});
	}

	private static final Charset USASCII_CHARSET = Charset.forName("US-ASCII");
	private static final char CR = '\r';
	private static final char LF = '\n';
	private static final char PATH_SEPARATOR = '/';

	private static final class FtpResponseReader implements CloseableByteBufferHandler, FtpClientHandler.Callback {
		private static enum State { // Postfixed by '_' when waiting for another response
			_CONNECTING,
			USER,
			PASS,
			TYPE_I,
			TYPE_A,
			MODE,
			CWD,
			CDUP,
			PASV,
			RETR,
			RETR_,
			NLST,
			NLST_,
		}

		private final String login;
		private final String password;
		private final CloseableByteBufferHandler write;
		private Address dataAddress;
		private CloseableByteBufferHandler data = null;
		private State state = State._CONNECTING;
		private final Deque<List<String>> toDownloadSplitPaths = new LinkedList<>();
		private final Deque<String> toDownloadRawPaths = new LinkedList<>();
		private boolean idle = false;
		private boolean dataFinished = false;
		private boolean retrFinished = false;
		private final Deque<String> currentDirectory = new LinkedList<>();
		private String goTo;
		private String fileToRetr;
		private String currentRawPath;

		private final Queue queue;

		private boolean closed = false;
		private final LineReader lineReader = new LineReader();
		private final FtpClientHandler handler;

		public FtpResponseReader(Queue queue, String login, String password, FtpClientHandler handler, CloseableByteBufferHandler write) {
			this.queue = queue;
			this.handler = handler;
			this.write = write;
			this.login = login;
			this.password = password;
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				write.close();
				handler.close();
			}
		}
		
		private FtpClientHandler.Callback.DownloadCallback downloadCallback = null;
		private FtpClientHandler.Callback.ListCallback listCallback = null;
		private final List<String> list = new LinkedList<>();

		@Override
		public void list(String path, ListCallback callback) {
			if (data != null) {
				data.close();
				data = null;
			}
			listCallback = callback;
			downloadCallback = null;
			list.clear();
			
			if (path != null) {
				if (path.charAt(path.length() - 1) == PATH_SEPARATOR) {
					throw new IllegalArgumentException("Path cannot finish with '/'");
				}
				path = path + '/';
			} else {
				path = "/";
			}
			download(path);
		}
		
		@Override
		public void download(String path, DownloadCallback handler) {
			if (data != null) {
				data.close();
				data = null;
			}
			downloadCallback = handler;
			listCallback = null;
			list.clear();

			if (path == null) {
				throw new NullPointerException();
			}
			if (path.charAt(path.length() - 1) == PATH_SEPARATOR) {
				throw new IllegalArgumentException("Path cannot finish with '/'");
			}
			download(path);
		}

		@Override
		public void handle(Address address, ByteBuffer buffer) {
			if (closed) {
				return;
			}

			try {
				while (true) {
					String line = lineReader.handle(buffer);
					if (line == null) {
						return;
					}
		
					LOGGER.debug("Line read: {}", line);
		
					if ((line.length() >= 1) && (line.charAt(0) == ' ')) {
						continue; // Ignored
					}
		
					if (line.length() < 4) {
						throw new IOException("Invalid response");
					}
		
					if (line.charAt(3) == '-') { // Comment
						continue; // Ignored
					}
		
					int code;
					try {
						code = Integer.parseInt(line.substring(0, 3));
					} catch (NumberFormatException e) {
						throw new IOException("Invalid response code");
					}
		
					String message = line.substring(4);
		
					handleLine(code, message);
				}
			} catch (IOException e) {
				if (data != null) {
					data.close();
					data = null;
				}
				if (!closed) {
					closed = true;
					write.close();
					handler.failed(e);
				}
			}
		}

		private void checkCode(int code, int... required) throws IOException {
			for (int r : required) {
				if (code == r) {
					return;
				}
			}
			throw new IOException("Response code " + code + " should be " + required);
		}

		private void writeLine(String line) {
			if (closed) {
				return;
			}
			write.handle(null, LineReader.toBuffer(line));
		}
		
		private void handleLine(int code, String message) throws IOException {
			switch (state) {
				case _CONNECTING:
					checkCode(code, 220);
					LOGGER.debug("Writing login: {}", login);
					writeLine("USER " + login);
					state = State.USER;
					break;
				case USER:
					checkCode(code, 331);
					LOGGER.debug("Writing password: {}", password);
					writeLine("PASS " + password);
					state = State.PASS;
					break;
				case PASS:
					if (code == 530) {
						closed = true;
						write.close();
						handler.authenticationFailed();
						return;
					}
					checkCode(code, 230);
					writeLine("MODE S");
					state = State.MODE;
					break;
				case MODE:
					checkCode(code, 200);
					if (!toDownloadSplitPaths.isEmpty()) {
						moveToDir();
					} else {
						idle = true;
					}
					break;
				case CWD:
					if (code == 550) {
						toDownloadSplitPaths.removeFirst();
						String rawPath = toDownloadRawPaths.removeFirst();
						if (downloadCallback != null) {
							DownloadCallback c = downloadCallback;
							downloadCallback = null;
							c.doesNotExist(rawPath);
						}
						if (listCallback != null) {
							ListCallback c = listCallback;
							listCallback = null;
							c.doesNotExist(rawPath);
						}

						if (!toDownloadSplitPaths.isEmpty()) {
							moveToDir();
						} else {
							idle = true;
						}
						return;
					}
					checkCode(code, 250);
					currentDirectory.addLast(goTo);
					moveToDir();
					break;
				case CDUP:
					checkCode(code, 250);
					currentDirectory.removeLast();
					moveToDir();
					break;
				case PASV:
					checkCode(code, 227);
					int u = message.indexOf('(');
					if (u < 0) {
						throw new IOException("Missing opening paranthesis");
					}
					int v = message.indexOf(')', u + 1);
					if (v < 0) {
						throw new IOException("Missing closing paranthesis");
					}
					List<String> ss = split(message.substring(u + 1, v), ',');
					if (ss.size() != 6) {
						throw new IOException("Invalid address");
					}
					String host = ss.get(0) + "." + ss.get(1) + "." + ss.get(2) + "." + ss.get(3);
					int port;
					try {
						port = (Integer.parseInt(ss.get(4)) * 256) + Integer.parseInt(ss.get(5));
					} catch (NumberFormatException e) {
						throw new IOException("Invalid address");
					}
					dataAddress = new Address(host, port);

					LOGGER.debug("Data address: {}", dataAddress);

					currentRawPath = toDownloadRawPaths.removeFirst();
					List<String> path = toDownloadSplitPaths.removeFirst();
					String file = path.get(path.size() - 1);

					if (file.isEmpty()) {
						new SocketReady(queue.getSelector(), queue.allocator()).connect(dataAddress, new ReadyConnection() {
							private final LineReader lineReader = new LineReader();
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								if (closed) {
									return;
								}
								if (listCallback == null) {
									return;
								}
								while (true) {
									String name = lineReader.handle(buffer);
									if (name == null) {
										break;
									}
									if (name.equals(".")) {
										continue;
									}
									if (name.equals("..")) {
										continue;
									}
									list.add(name);
								}
							}
							
							@Override
							public void failed(IOException e) {
								if (closed) {
									return;
								}
								if (listCallback == null) {
									return;
								}
								LOGGER.debug("Connection failed: {}", dataAddress, e);
								listCallback.failed(e);
							}
							
							@Override
							public void connected(FailableCloseableByteBufferHandler write) {
								data = write;
							}
							
							@Override
							public void close() {
								if (closed) {
									return;
								}
								dataFinished = true;
								if (retrFinished) {
									if (listCallback != null) {
										listCallback.handle(list);
									}
									dataFinished = false;
									retrFinished = false;
								}
							}
						});

						LOGGER.debug("Listing: {}", currentRawPath);

						writeLine("TYPE A");
						state = State.TYPE_A;
					} else {
						new SocketReady(queue.getSelector(), queue.allocator()).connect(dataAddress, new ReadyConnection() {
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								if (closed) {
									return;
								}
								if (downloadCallback == null) {
									return;
								}
								downloadCallback.handle(null, buffer);
							}
							
							@Override
							public void failed(IOException e) {
								if (closed) {
									return;
								}
								if (downloadCallback == null) {
									return;
								}
								LOGGER.debug("Connection failed: {}", dataAddress, e);
								downloadCallback.failed(e);
							}
							
							@Override
							public void connected(FailableCloseableByteBufferHandler write) {
								data = write;
							}
							
							@Override
							public void close() {
								if (closed) {
									return;
								}
								dataFinished = true;
								if (retrFinished) {
									if (downloadCallback != null) {
										downloadCallback.close();
									}
									dataFinished = false;
									retrFinished = false;
								}
							}
						});

						LOGGER.debug("Getting: {}", file);

						fileToRetr = file;
						writeLine("TYPE I");
						state = State.TYPE_I;
					}
					break;
				case TYPE_I:
					checkCode(code, 200);
					writeLine("RETR " + fileToRetr);
					state = State.RETR;
					break;
				case TYPE_A:
					checkCode(code, 200);
					writeLine("NLST");
					state = State.NLST;
					break;
				case RETR:
					if (code == 550) {
						if (downloadCallback != null) {
							DownloadCallback c = downloadCallback;
							downloadCallback = null;
							c.doesNotExist(currentRawPath);
						}
						if (listCallback != null) {
							ListCallback c = listCallback;
							listCallback = null;
							c.doesNotExist(currentRawPath);
						}
						if (data != null) {
							data.close();
							data = null;
						}

						if (!toDownloadSplitPaths.isEmpty()) {
							moveToDir();
						} else {
							idle = true;
						}
						return;
					}
					checkCode(code, 150, 125);
					state = State.RETR_;
					break;
				case RETR_:
					checkCode(code, 226);
					retrFinished = true;
					if (dataFinished) {
						if (downloadCallback != null) {
							DownloadCallback c = downloadCallback;
							downloadCallback = null;
							c.close();
						}
						if (listCallback != null) {
							ListCallback c = listCallback;
							listCallback = null;
							c.handle(list);
						}
						dataFinished = false;
						retrFinished = false;
					}
					if (!toDownloadSplitPaths.isEmpty()) {
						moveToDir();
					} else {
						idle = true;
					}
					break;
				case NLST:
					if (code == 550) {
						if (downloadCallback != null) {
							DownloadCallback c = downloadCallback;
							downloadCallback = null;
							c.doesNotExist(currentRawPath);
						}
						if (listCallback != null) {
							ListCallback c = listCallback;
							listCallback = null;
							c.doesNotExist(currentRawPath);
						}
						if (data != null) {
							data.close();
							data = null;
						}

						if (!toDownloadSplitPaths.isEmpty()) {
							moveToDir();
						} else {
							idle = true;
						}
						return;
					}
					checkCode(code, 150, 125);
					state = State.NLST_;
					break;
				case NLST_:
					checkCode(code, 226);
					retrFinished = true;
					if (dataFinished) {
						if (downloadCallback != null) {
							DownloadCallback c = downloadCallback;
							downloadCallback = null;
							c.close();
						}
						if (listCallback != null) {
							ListCallback c = listCallback;
							listCallback = null;
							c.handle(list);
						}
						dataFinished = false;
						retrFinished = false;
					}
					if (data != null) {
						data.close();
						data = null;
					}

					if (!toDownloadSplitPaths.isEmpty()) {
						moveToDir();
					} else {
						idle = true;
					}
					break;
			}
		}

		private void moveToDir() {
			List<String> path = toDownloadSplitPaths.getFirst();
			int i = 0;
			boolean goUp = false;
			for (String c : currentDirectory) {
				if (i == (path.size() - 1)) {
					goUp = true;
					break;
				}

				String p = path.get(i);

				if (!c.equals(p)) {
					goUp = true;
					break;
				}

				i++;
			}

			if (goUp) {
				LOGGER.debug("CD ..");
				writeLine("CDUP");
				state = State.CDUP;
				return;
			}

			if (i < (path.size() - 1)) {
				goTo = path.get(i);
				LOGGER.debug("CD " + goTo);
				writeLine("CWD " + goTo);
				state = State.CWD;
				return;
			}

			writeLine("PASV");
			state = State.PASV;
		}
		
		private void download(String path) {
			if (path.isEmpty()) {
				throw new IllegalArgumentException("Path cannot be empty");
			}
			if (path.charAt(0) != PATH_SEPARATOR) {
				throw new IllegalArgumentException("Path must be absolute (must start with '/')");
			}
			path = path.substring(1);
			toDownloadRawPaths.addLast(path);
			toDownloadSplitPaths.addLast(split(path, PATH_SEPARATOR));
			if (!idle) {
				return;
			}

			idle = false;
			moveToDir();
		}

		private static List<String> split(String s, char c) {
			return Splitter.on(c).splitToList(s);
			/*
			List<String> l = new LinkedList<String>();
			int j = 0;
			while (true) {
				int i = s.indexOf(c, j);
				if (i < 0) {
					l.add(s.substring(j));
					break;
				}
				l.add(s.substring(j, i));
				j = i + 1;
			}
			return l;
			*/
		}
	}
	
	private static final class LineReader {

		private final StringBuilder line = new StringBuilder();
		private boolean lastCharCR = false;

		public LineReader() {
		}

		public static ByteBuffer toBuffer(String s) {
			return ByteBuffer.wrap((s + CR + LF).getBytes(USASCII_CHARSET));
		}

		public String handle(ByteBuffer buffer) {
			while (true) {
				if (!buffer.hasRemaining()) {
					return null;
				}
				char c = (char) buffer.get(); // ok on charset US-ASCII
				if (lastCharCR) {
					lastCharCR = false;
					if (c == LF) {
						String l = line.toString();
						line.setLength(0);
						return l;
					} else {
						line.append(CR);
						if (c == CR) {
							lastCharCR = true;
						} else {
							line.append(c);
						}
					}
				} else if (c == CR) {
					lastCharCR = true;
				} else {
					line.append(c);
				}
			}
		}
	}

}
