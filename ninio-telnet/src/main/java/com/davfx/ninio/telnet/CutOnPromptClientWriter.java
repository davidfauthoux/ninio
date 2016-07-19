package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Disconnectable;

public interface CutOnPromptClientWriter extends Disconnectable {
	void write(String command, String prompt, CutOnPromptClientReceiver callback);
}