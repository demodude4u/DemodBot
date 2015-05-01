package com.vectorcat.irc.demodbot.feature;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.User;
import com.vectorcat.irc.demodbot.event.AdminCommand;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;

@Singleton
public class ExperimentalFeature {

	private final IRCState state;
	private final LongMessageSolution longMessageSolution;

	@Inject
	ExperimentalFeature(HelpFeature helpFeature, EventBus bus, IRCState state,
			LongMessageSolution longMessageSolution) {
		this.state = state;
		this.longMessageSolution = longMessageSolution;
		bus.register(this);
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Experimental Features",
						"This could have anything my creator is messing around with...");
	}

	@Subscribe
	public void onAdminCommand(AdminCommand event) {
		if (event.getCommand().equals("DEBUGSTATE")) {
			Builder<String> message = ImmutableList.builder();
			for (Channel channel : state.getChannels()) {
				message.add("----- " + channel + " -----");
				for (User user : channel.getUsers()) {
					message.add(user.toString());
				}
			}

			URL url;
			try {
				url = longMessageSolution.provideSolution(message.build(),
						"Debug State as of " + new Date());
				event.getTarget().reply(event.getUser(), url.toString());
			} catch (IOException e) {
				event.getTarget().reply(event.getUser(),
						"Something broke. [" + e.getMessage() + "] :(");
			}
		}
	}
}
