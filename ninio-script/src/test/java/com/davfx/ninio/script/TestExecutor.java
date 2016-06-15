package com.davfx.ninio.script;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestExecutor {
	public static void main(String[] args) {
		for (int ii = 0; ii < 10; ii++) {
			final long[] t = new long[] { 0L }; 
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			t[0] = System.currentTimeMillis();
			for (int i = 0; i < 10000; i++) {
				final byte[] b = new byte[10 * 1024];
				for (int j = 0; j < b.length; j++) {
					//b[j] = (byte) j;
				}
			}
			long d = System.currentTimeMillis() - t[0];
			System.out.println(d);
		}
		for (int ii = 0; ii < 10; ii++) {
			Executor e = Executors.newSingleThreadExecutor();
			final long[] t = new long[] { 0L }; 
			e.execute(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
					t[0] = System.currentTimeMillis();
				}
			});
			for (int i = 0; i < 10000; i++) {
				final byte[] b = new byte[10 * 1024];
				e.execute(new Runnable() {
					@Override
					public void run() {
						for (int j = 0; j < b.length; j++) {
							//b[j] = (byte) j;
						}
					}
				});
			}
			final AtomicBoolean bb = new AtomicBoolean(false);
			e.execute(new Runnable() {
				@Override
				public void run() {
					long d = System.currentTimeMillis() - t[0];
					System.out.println("e " + d);
					synchronized (bb) {
						bb.set(true);
						bb.notifyAll();
					}
				}
			});
			synchronized (bb) {
				while (!bb.get()) {
					try {
						bb.wait();
					} catch (InterruptedException ee) {
					}
				}
			}
			System.out.println("Next");
		}
	}
}
