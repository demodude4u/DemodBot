package com.vectorcat.irc.demodbot.feature;

import java.util.Set;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.User;
import com.vectorcat.irc.demodbot.event.AdminCommand;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.event.recv.IRCRecvCommand;
import com.vectorcat.irc.event.send.IRCSendJoin;
import com.vectorcat.irc.event.send.IRCSendMessage;
import com.vectorcat.irc.event.send.IRCSendPart;
import com.vectorcat.irc.event.send.IRCSendRaw;

@Singleton
public class AdminFeature {

	private final IRCControl control;

	private final Set<User> adminUsers = Sets.newLinkedHashSet();

	private final EventBus bus;

	private final IRCState state;

	@Inject
	AdminFeature(HelpFeature helpFeature, EventBus bus, IRCControl control,
			IRCState state) {
		this.bus = bus;
		this.control = control;
		this.state = state;
		bus.register(this);
	}

	public synchronized void addAdmins(String... users) {
		for (String user : users) {
			adminUsers.add(control.getUser(user));
		}
	}

	@Subscribe
	public void onCommand(IRCRecvCommand event) {
		if (adminUsers.contains(event.getUser())) {
			bus.post(new AdminCommand(event.getTarget(), event.getUser(), event
					.getLogin(), event.getHostname(), event.getRawMessage(),
					event.getMessage(), event.isDirectedAtMe(), event
							.getCommand(), event.getArguments()));
		}
	}

	@Subscribe
	public void onAdminCommand(AdminCommand event) {
		if (event.getCommand().equals("MUTE")) {
			control.mute();
		}

		if (event.getCommand().equals("UNMUTE")) {
			control.unmute();
		}

		if (event.getCommand().equals("KILL")) {
			System.exit(-2);// XXX
		}

		if (event.getCommand().equals("RAW")) {
			if (!event.getArguments().isEmpty()) {
				bus.post(new IRCSendRaw(event.getArguments().toString()));
			}
		}

		if (event.getCommand().equals("JOIN")) {
			if (event.getArguments().size() == 1) {
				bus.post(new IRCSendJoin(control.getChannel(event
						.getArguments().get(0))));
			}
		}

		if (event.getCommand().equals("PART")) {
			if (event.getArguments().size() == 1) {
				bus.post(new IRCSendPart(control.getChannel(event
						.getArguments().get(0))));
			}
		}

		if (event.getCommand().equals("SAY")) {
			if (event.getArguments().size() > 1) {
				bus.post(new IRCSendMessage(control.getTarget(event
						.getArguments().get(0)), event.getArguments().toString(
						1)));
			}
		}
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Administration Feature", "For all my admin things!",
						"To get access or help about admin commands, contact my creator.");
	}
}
