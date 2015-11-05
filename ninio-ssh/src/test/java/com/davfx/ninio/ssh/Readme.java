package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Charsets;

public final class Readme {
	public static void main(String[] args) throws Exception {
		for (Provider p : Security.getProviders()) {
			Security.removeProvider(p.getName());
		}
		Security.addProvider(new BouncyCastleProvider());
		
		final String login = "www";// "<your-login>";
		final String password = "ww414vkw";// "<your-password>";
		
		new Ssh().withLogin(login).withKey("TODO", "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCj6OAravsptm4S4ZmIQzwu0/oTxNIFq7Rz55SHcruDA1geuXE7/ErKSDZs1p6sYjqFZuoSXadrayWzkgzR39vRZukY+txDsYRod7xJaN6Rc6+S2xlDt9mHtMIIbJvHZ1G3L8a5UksJ5veP9ix/tR7cOzuxojUGWtV55i7oryooL7w4vMetol2JM0a0V+A3436j1vXOsnG6OsypLfDBFK+aPQA52Ewc1bo0d4kucT6AeYTNM8ERo09uVOGzLbgjpwPhKxOQOd1fDanv4yVW/8wwcyMl2WuvPTQ8839nQiT5uHTu3dE3k0mow9zQg+5Z3zI86z8xh/fMXrVCfQckHh/X david.fauthoux@gmail.com")
		.to(new Address("w.davfx.com", Ssh.DEFAULT_PORT)).create().connect(new ReadyConnection() {
			
			private FailableCloseableByteBufferHandler write;

			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void close() {
				System.out.println("Closed");
			}
			
			private void send(String line) {
				write.handle(null, ByteBuffer.wrap((line + '\n').getBytes(Charsets.UTF_8)));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				System.out.print(s);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
				System.out.println("--Connected--");
				send("echo TEST");
			}
		});
		
		Thread.sleep(300000);
	}
}
