package com.vectorcat.irc.demodbot;

import java.io.IOException;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.vectorcat.irc.Target;
import com.vectorcat.irc.User;
import com.vectorcat.irc.event.recv.IRCRecvCommand;

public class SpellSuggestFeature {

	GoogleSuggest googleSuggest = new GoogleSuggest();

	@Inject
	SpellSuggestFeature(EventBus bus) {
		bus.register(this);
	}

	@Subscribe
	public void onCommand(IRCRecvCommand event) {
		String command = event.getCommand();

		if (command.equals("SP")) {
			spellCheck(event.getTarget(), event.getUser(),
					event.getArgumentString());
		}
	}

	public void spellCheck(Target target, User user, String message) {
		try {
			Thread.sleep(1000);// XXX
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		try {
			String suggestedSpelling = googleSuggest.suggestSpelling(message);
			if (suggestedSpelling == null) {
				// target.reply(user, "Looks fine to me, or I'm confused...");
			} else {
				target.reply(user, "\"" + suggestedSpelling + "\"");
			}
		} catch (IOException e) {
			e.printStackTrace();
			target.reply(user,
					"I had problems with your request. :( [" + e.getMessage()
							+ "]");
		}
	}
}
