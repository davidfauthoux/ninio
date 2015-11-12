ninio-ftp
=========

```java
String login = "<your-login>";
String password = "<your-password>";

new Ftp()
	.withLogin(login).withPassword(password)
.to(new Address("<ftp-host>", Ftp.DEFAULT_PORT)).create().connect(new FtpClientHandler() {
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void authenticationFailed() {
		System.out.println("Authentication failed");
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	@Override
	public void launched(Callback callback) {
		callback.list("/", new ListCallback() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void handle(Iterable<String> content) {
				System.out.println(content);
			}
			@Override
			public void doesNotExist(String path) {
				System.out.println("Does not exist: " + path);
			}
		});
	}
});
```
		
```java
String login = "<your-login>";
String password = "<your-password>";

new Ftp()
	.withLogin(login).withPassword(password)
.to(new Address("<ftp-host>", Ftp.DEFAULT_PORT)).create().connect(new FtpClientHandler() {
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void authenticationFailed() {
		System.out.println("Authentication failed");
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	@Override
	public void launched(Callback callback) {
		callback.download("/todownload.txt", new ToFileDownloadCallback(new File("downloaded.txt"), new Failable() {
			@Override
			public void failed(IOException e) {
				if (e == null) {
					System.out.println("Done");
					return;
				}
				e.printStackTrace();
			}
		}));
	}
});
```
