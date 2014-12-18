package com.vectorcat.irc.demodbot.feature;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.ocpsoft.prettytime.PrettyTime;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.User;
import com.vectorcat.irc.demodbot.event.AdminCommand;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall.Responder;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.IRCServerConnect;
import com.vectorcat.irc.event.recv.IRCRecvChannelMessage;
import com.vectorcat.irc.event.recv.IRCRecvCommand;
import com.vectorcat.irc.event.recv.IRCRecvJoin;
import com.vectorcat.irc.event.recv.IRCRecvNickChange;
import com.vectorcat.irc.event.recv.IRCRecvPart;
import com.vectorcat.irc.event.recv.IRCRecvQuit;

@Singleton
public class LastSeenFeature {

	@Data
	@EqualsAndHashCode
	private static class UserEntry {
		private final User user;
		private final Channel channel;
		private long lastPart = -1;
		private long lastUpdate = -1;
		private long lastJoin = -1;
	}

	private final IRCControl control;
	private final IRCState state;

	private final LongMessageSolution longMessageSolution;

	private final Table<Channel, User, UserEntry> entries = HashBasedTable
			.create();

	@Inject
	LastSeenFeature(AdminFeature adminFeature, HelpFeature helpFeature,
			EventBus bus, IRCControl control, IRCState state,
			LongMessageSolution longMessageSolution) {
		this.control = control;
		this.state = state;
		this.longMessageSolution = longMessageSolution;
		bus.register(this);
	}

	private UserEntry getEntry(User user, Channel channel) {
		UserEntry ret = entries.get(user, channel);
		if (ret == null) {
			entries.put(channel, user, ret = new UserEntry(user, channel));
		}
		return ret;
	}

	private void joinUser(User user, Channel channel) {
		UserEntry entry = getEntry(user, channel);
		entry.lastJoin = System.currentTimeMillis();
	}

	@Subscribe
	public void onAdminCommand(AdminCommand event) {
		if (event.getCommand().equals("CLEARSEEN")) {
			entries.clear();
			for (Channel channel : state.getMyUser().getChannels()) {
				for (User user : channel.getUsers()) {
					updateUser(user, channel);
				}
			}
			event.getTarget().reply(event.getUser(), "User Reports cleared!");
		}
	}

	@Subscribe
	public void onChannelMessage(final IRCRecvChannelMessage event) {
		updateUser(event.getUser(), event.getChannel());
	}

	@Subscribe
	public void onCommand(final IRCRecvCommand event) {
		if (!event.getTarget().isChannel()) {
			return;
		}

		Channel channel = event.getTarget().asChannel();

		if (event.getCommand().equals("SEEN")) {
			Map<User, UserEntry> channelEntries = entries.row(channel);
			TreeSet<UserEntry> sortedEntries = new TreeSet<>(
					new Comparator<UserEntry>() {
						@Override
						public int compare(UserEntry o1, UserEntry o2) {
							return o1.getUser().getIdentifier()
									.compareTo(o2.getUser().getIdentifier());
						}
					});
			sortedEntries.addAll(channelEntries.values());

			final PrettyTime p = new PrettyTime();
			List<String> statuses = ImmutableList.copyOf(Collections2
					.transform(sortedEntries,
							new Function<UserEntry, String>() {
								@Override
								public String apply(UserEntry entry) {
									long lastJoin = entry.getLastJoin();
									boolean join = lastJoin != -1;
									String fJoin = p.format(new Date(lastJoin));

									long lastPart = entry.getLastPart();
									boolean part = lastPart != -1
											&& lastPart > lastJoin;
									String fPart = p.format(new Date(lastPart));

									long lastUpdate = entry.getLastUpdate();
									boolean update = lastUpdate != -1;
									String fUpdate = p.format(new Date(
											lastUpdate));

									String status = "[INVALID] J=" + fJoin
											+ ", P=" + fPart + ", U=" + fUpdate;

									if (!join && !part && update) {
										status = "Activity seen " + fUpdate
												+ ".";

									} else if (part && !update) {
										status = "Left " + fPart + ".";

									} else if (part && update) {
										status = "Left " + fPart
												+ ", activity seen " + fUpdate
												+ ".";

									} else if (join && !part && !update) {
										status = "Joined " + fJoin + ".";

									} else if (join && !part && update) {
										status = "Joined " + fJoin
												+ ", activity seen " + fUpdate
												+ ".";

									}

									return String.format("%-30s",
											entry.getUser() + ":").replace(' ',
											'.')
											+ " " + status;
								}

							}));

			URL url;
			try {
				url = longMessageSolution.provideSolution(statuses,
						"User Report in " + channel + " as of " + new Date());
				channel.reply(event.getUser(), url.toString());
			} catch (IOException e) {
				channel.reply(event.getUser(),
						"Something broke. [" + e.getMessage() + "] :(");
			}
		}
	}

	@Subscribe
	public void onCommandRollCall(CommandRollCall event) {
		Responder responder = event.getResponder();
		responder
				.reply("LAST SEEN",
						"!seen",
						"Replies with a report of all of the users seen in this channel, along ",
						"with some statistics.");
	}

	@Subscribe
	public void onConnect(IRCServerConnect event) {
		entries.clear();
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Last Seen Feature",
						"This feature monitors the activity of users within a channel and reports ",
						"the statistics.");
	}

	@Subscribe
	public void onJoin(IRCRecvJoin event) {
		joinUser(event.getUser(), event.getChannel());
	}

	@Subscribe
	public void onPart(IRCRecvPart event) {
		partUser(event.getUser(), event.getChannel());
	}

	@Subscribe
	public void onQuit(IRCRecvQuit event) {
		for (Channel channel : event.getUser().getChannels()) {
			partUser(event.getUser(), channel);
		}
	}

	@Subscribe
	public void onNickChange(IRCRecvNickChange event) {
		for (Channel channel : event.getUser().getChannels()) {
			partUser(event.getNewUser(), channel);
			joinUser(event.getNewUser(), channel);
		}
	}

	private void partUser(User user, Channel channel) {
		UserEntry entry = getEntry(user, channel);
		entry.lastPart = System.currentTimeMillis();
	}

	private void updateUser(User user, Channel channel) {
		getEntry(user, channel).lastUpdate = System.currentTimeMillis();
	}

}
