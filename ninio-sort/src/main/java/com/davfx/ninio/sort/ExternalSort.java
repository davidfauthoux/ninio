package com.davfx.ninio.sort;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;

public final class ExternalSort<T> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSort.class);
	
	/*%%
	private static final Config CONFIG = ConfigUtils.load(ExternalSort.class);
	private static final int NUMBER_OF_LINES_TO_SPLIT = CONFIG.getInt("split");
	private static final int THREADS = CONFIG.getInt("threads");
	private static final ExecutorService DEFAULT_EXECUTOR = Executors.newFixedThreadPool(THREADS, new ClassThreadFactory(ExternalSort.class, true));
	
	public static <T> ExternalSort<T> create(Comparator<T> comparator, Function<T, ByteBuffer> toByteBuffer, Function<ByteBuffer, T> fromByteBuffer) {
		return new ExternalSort<T>(DEFAULT_EXECUTOR, NUMBER_OF_LINES_TO_SPLIT, comparator, toByteBuffer, fromByteBuffer);
	}
	public static <T extends Comparable<T>> ExternalSort<T> create(Function<T, ByteBuffer> toByteBuffer, Function<ByteBuffer, T> fromByteBuffer) {
		return create(new Comparator<T>() {
			@Override
			public int compare(T a, T b) {
				return a.compareTo(b);
			}
		}, toByteBuffer, fromByteBuffer);
	}
	*/
	
	private final Comparator<T> comparator;
	private final int numberOfLinesToSplit;
	private final Function<T, ByteBuffer> toByteBuffer;
	private final Function<ByteBuffer, T> fromByteBuffer;
	private final ExecutorService executor;

	public ExternalSort(ExecutorService executor, int numberOfLinesToSplit, Comparator<T> comparator, Function<T, ByteBuffer> toByteBuffer, Function<ByteBuffer, T> fromByteBuffer) {
		this.comparator = comparator;
		this.toByteBuffer = toByteBuffer;
		this.fromByteBuffer = fromByteBuffer;
		this.executor = executor;
		this.numberOfLinesToSplit = numberOfLinesToSplit;
	}

	public Iterable<T> sorted(final Iterable<T> input) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final List<Future<File>> files = new LinkedList<>();
					private final PriorityQueue<InBinaryFileBuffer> queue;
					private final List<InBinaryFileBuffer> buffers = new LinkedList<>();
					{
						Iterator<T> i = input.iterator();
						while (i.hasNext()) {
							final Collection<T> l = new LinkedList<>();
							int linesCount = 0;
							while (i.hasNext()) {
								T line = i.next();
			
								l.add(line);
			
								linesCount++;
								if (linesCount >= numberOfLinesToSplit) {
									break;
								}
							}
			
							files.add(executor.submit(new Callable<File>() {
								@Override
								public File call() {
									try {
										File tempFile = File.createTempFile("externalsort", ".txt");
										LOGGER.trace("Temp file: {}", tempFile);
										tempFile.deleteOnExit();
								
										try (OutBinaryFileBuffer w = new OutBinaryFileBuffer(tempFile)) {
											for (T r : Ordering.from(comparator).sortedCopy(l)) {
												w.write(r);
											}
										}
			
										return tempFile;
									} catch (IOException e) {
										LOGGER.error("Could not create externalsort temp file", e);
										return null;
									}
								}
							}));
						}
						
						try {
							for (Future<File> ff : files) {
								File f;
								try {
									f = ff.get();
								} catch (Exception e) {
									throw new IOException(e);
								}
								if (f == null) {
									throw new IOException();
								}
								buffers.add(new InBinaryFileBuffer(f));
							}
						} catch (IOException e) {
							LOGGER.error("Invalid externalsort temp files", e);
							close();
							buffers.clear();
							files.clear();
						}
			
						queue = new PriorityQueue<InBinaryFileBuffer>(1, new Comparator<InBinaryFileBuffer>() {
							@Override
							public int compare(InBinaryFileBuffer i, InBinaryFileBuffer j) {
								return comparator.compare(i.peek(), j.peek());
							}
						});
						
						for (InBinaryFileBuffer b : buffers) {
							if (!b.empty()) {
								queue.add(b);
							}
						}
					}
					
					private T next;
					{
						getNext();
					}
					private void getNext() {
						if (queue.isEmpty()) {
							next = null;
							return;
						}
						
						InBinaryFileBuffer b = queue.poll();
						
						try {
							next = b.pop();
						} catch (IOException e) {
							LOGGER.error("Error while reading externalsort temp files", e);
							close();
							next = null;
						}
						
						if (b.empty()) {
							try {
								b.close();
							} catch (IOException ioe) {
							}
						} else {
							queue.add(b); // Add it back
						}
					}
					@Override
					public T next() {
						T current = next;
						getNext();
						return current;
					}
					
					@Override
					public boolean hasNext() {
						return (next != null);
					}
					
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
					
					private void close() {
						for (Future<File> ff : files) {
							try {
								ff.get();
							} catch (Exception e) {
							}
						}
						for (InBinaryFileBuffer b : buffers) {
							try {
								b.close();
							} catch (IOException ioe) {
							}
						}
						for (Future<File> ff : files) {
							File f;
							try {
								f = ff.get();
							} catch (Exception e) {
								f = null;
							}
							if (f != null) {
								f.delete();
							}
						}
					}
					
					@Override
					protected void finalize() {
						close();
					}
				};
			}
		};
	}

	private final class InBinaryFileBuffer implements AutoCloseable {
		private FileChannel r;
		private T cache;
		private ByteBuffer sizeBuffer = ByteBuffer.allocate(Ints.BYTES);

		private void readFully(ByteBuffer b) throws IOException {
			while (b.position() < b.capacity()) {
				if (r.read(b) < 0) {
					throw new IOException();
				}
			}
		}
		private void read() throws IOException {
			while (true) {
				if (r.position() == r.size()) {
					cache = null;
					return;
				}
				sizeBuffer.clear();
				readFully(sizeBuffer);
				sizeBuffer.flip();
				ByteBuffer b = ByteBuffer.allocate(sizeBuffer.getInt());
				readFully(b);
				b.flip();
				cache = fromByteBuffer.apply(b);
				if (cache != null) {
					break;
				}
			}
		}
		
		@SuppressWarnings("resource")
		public InBinaryFileBuffer(File f) throws IOException {
			r = new FileInputStream(f).getChannel();
			try {
				read();
			} catch (IOException e) {
				try {
					r.close();
				} catch (IOException ioe) {
				}
				throw e;
			}
		}

		@Override
		public void close() throws IOException {
			r.close();
		}

		public boolean empty() {
			return (cache == null);
		}

		public T peek() {
			return cache;
		}

		public T pop() throws IOException {
			T s = peek();
			read();
			return s;
		}
	}
	
	private final class OutBinaryFileBuffer implements AutoCloseable {
		private FileChannel r;
		private ByteBuffer sizeBuffer = ByteBuffer.allocate(Ints.BYTES);

		public void write(T t) throws IOException {
			ByteBuffer b = toByteBuffer.apply(t);
			if (b != null) {
				sizeBuffer.clear();
				sizeBuffer.putInt(b.remaining());
				sizeBuffer.flip();
				r.write(sizeBuffer);
				r.write(b);
			}
		}
		
		@SuppressWarnings("resource")
		public OutBinaryFileBuffer(File f) throws IOException {
			r = new FileOutputStream(f).getChannel();
		}

		@Override
		public void close() throws IOException {
			r.close();
		}
	}
}
