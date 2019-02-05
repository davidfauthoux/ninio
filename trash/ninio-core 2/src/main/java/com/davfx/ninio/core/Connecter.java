package com.davfx.ninio.core;

public interface Connecter extends Connected {
	void connect(Connection callback);
}
