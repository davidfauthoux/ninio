package com.davfx.ninio.telnet;

import com.davfx.ninio.core.ConnectingClosingFailing;

public interface CutOnPromptClientHandler extends ConnectingClosingFailing {
	void connected(CutOnPromptClientWriter write);
}