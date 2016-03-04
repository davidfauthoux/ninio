package com.davfx.ninio.core.v3;

public interface Acceptable {
	interface Listening {
		void connecting(Connectable connectable);
	}

	void accepting(Accepting accepting);
	void failing(Failing failing);

	void accept(Listening listening);
	void close();
}
