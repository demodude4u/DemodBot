package com.vectorcat.irc.demodbot;

import java.util.Map;

import com.google.code.chatterbotapi.ChatterBot;
import com.google.code.chatterbotapi.ChatterBotFactory;
import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotType;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.vectorcat.irc.event.recv.IRCRecvDirectedMessage;

public class CleverBotFeature {

	private final Map<String, ChatterBotSession> sessions = Maps.newHashMap();

	private final ChatterBot chatterBot;

	@Inject
	CleverBotFeature(EventBus bus) throws Exception {
		bus.register(this);

		ChatterBotFactory factory = new ChatterBotFactory();
		chatterBot = factory.create(ChatterBotType.JABBERWACKY);
	}

	private ChatterBotSession getSession(String identity) {
		ChatterBotSession ret = sessions.get(identity);
		if (ret == null) {
			sessions.put(identity, ret = chatterBot.createSession());
		}
		return ret;
	}

	@Subscribe
	public void onDirectedMessage(IRCRecvDirectedMessage event) throws Exception {
		String message = event.getMessage();

		ChatterBotSession session = getSession(event.getUser().getIdentifier());

		String response = session.think(message);
		if (event.getTarget().isChannel()) {
			response = event.getUser() + ": " + response;
		}

		event.getTarget().message(response);
	}
}
