package com.vectorcat.irc.demodbot.event.help;

import lombok.Data;

import com.vectorcat.irc.User;

@Data
public class CommandRollCall {
	public static interface Responder {
		public void reply(String category, String command,
				String... description);
	}

	private final User user;
	private final Responder responder;
}
