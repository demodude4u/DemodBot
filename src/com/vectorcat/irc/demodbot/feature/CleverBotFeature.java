package com.vectorcat.irc.demodbot.feature;

import java.util.Map;

import com.google.code.chatterbotapi.ChatterBot;
import com.google.code.chatterbotapi.ChatterBotFactory;
import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotType;
import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.event.recv.IRCRecvDirectedMessage;

@Singleton
public class CleverBotFeature {

	private final Map<String, ChatterBotSession> sessions = Maps.newHashMap();

	private final ChatterBot chatterBot;

	private final IRCState state;

	@Inject
	CleverBotFeature(HelpFeature helpFeature, EventBus bus, IRCState state)
			throws Exception {
		this.state = state;
		bus.register(this);

		ChatterBotFactory factory = new ChatterBotFactory();
		chatterBot = factory.create(ChatterBotType.CLEVERBOT);
	}

	private ChatterBotSession getSession(String identity) {
		ChatterBotSession ret = sessions.get(identity);
		if (ret == null) {
			sessions.put(identity, ret = chatterBot.createSession());
		}
		return ret;
	}

	@Subscribe
	public void onDirectedMessage(IRCRecvDirectedMessage event)
			throws Exception {
		String message = event.getMessage();

		if (!message.isEmpty() && message.trim().charAt(0) == '!') {
			// Ignore Commands
			return;
		}

		if (state.getMyUser().equals(event.getUser())) {
			event.getTarget().message("Sorry, I was talking to myself there.");
			return;
		}

		ChatterBotSession session = getSession(event.getUser().getIdentifier());

		event.getTarget().reply(event.getUser(), session.think(message));
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder().reply("ChatterBot Feature",
				"When you talk to me, I respond using Jabberwacky.",
				"... I may or may not be retarded.");
	}
}
