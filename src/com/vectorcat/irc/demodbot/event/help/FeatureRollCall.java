package com.vectorcat.irc.demodbot.event.help;

import lombok.Data;

@Data
public class FeatureRollCall {
	public static interface Responder {
		public void reply(String name, String... description);
	}

	private final Responder responder;
}
