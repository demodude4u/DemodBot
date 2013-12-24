package com.vectorcat.irc.demodbot.util;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public interface LongMessageSolution {
	public URL provideSolution(List<String> messages, String title) throws IOException;
}
