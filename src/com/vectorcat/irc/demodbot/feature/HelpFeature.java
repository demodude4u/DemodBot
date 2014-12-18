package com.vectorcat.irc.demodbot.feature;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import lombok.Data;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.Target;
import com.vectorcat.irc.User;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall.Responder;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.recv.IRCRecvCommand;

@Singleton
public class HelpFeature {
	@Data
	private class FinishCommandCommands {
		private final Multimap<String, String> messageMap;
		private final Target target;
		private final User user;
	}

	@Data
	private class FinishCommandFeatures {
		private final Multimap<String, String> messageMap;
		private final Target target;
		private final User user;
	}

	private final LongMessageSolution longMessageSolution;
	private final EventBus bus;

	@Inject
	HelpFeature(EventBus bus, LongMessageSolution longMessageSolution) {
		this.bus = bus;
		this.longMessageSolution = longMessageSolution;

		bus.register(this);
	}

	private List<String> buildMessageMap(String title,
			Multimap<String, String> messageMap) {
		ImmutableList.Builder<String> builder = ImmutableList.builder();
		builder.add("########## " + title + " ##########");

		for (String category : messageMap.keySet()) {
			builder.add("\n").add("(--( " + category + " )--)");
			builder.addAll(messageMap.get(category));
		}

		return builder.build();
	}

	private <K extends Comparable<? super K>, V> Multimap<K, V> createTreeArrayListMultimap() {
		return Multimaps.newListMultimap(Maps.<K, Collection<V>> newTreeMap(),
				new Supplier<List<V>>() {
					@Override
					public List<V> get() {
						return Lists.newArrayList();
					}
				});
	}

	@Subscribe
	public void onCommand(IRCRecvCommand event) {
		if (event.getCommand().equals("HELP")) {
			event.getTarget().reply(
					event.getUser(),
					"Type !commands to see what you can say,"
							+ " or type !features to see what"
							+ " is installed!");
		}
		if (event.getCommand().equals("COMMANDS")) {
			postCommands(event.getTarget(), event.getUser());
		}
		if (event.getCommand().equals("FEATURES")) {
			postFeatures(event.getTarget(), event.getUser());
		}
	}

	@Subscribe
	public void onCommandRollCall(CommandRollCall event) {
		Responder responder = event.getResponder();
		responder.reply("HELP", "!help");
		responder
				.reply("HELP", "!commands",
						"Lists off all commands being offered by my installed features.");
		responder.reply("HELP", "!features",
				"Lists off all currently installed features.");
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Help Feature",
						"I gather these descriptions and show them to you.",
						"I also gather all the information I can about the commands available too.");
	}

	@Subscribe
	public void onFinishCommandCommands(FinishCommandCommands event) {
		try {
			List<String> messages = buildMessageMap("COMMANDS",
					event.getMessageMap());
			URL url = longMessageSolution.provideSolution(messages,
					"Commands as of " + new Date());
			event.getTarget().reply(event.getUser(), "Commands: " + url);
		} catch (Exception e) {
			e.printStackTrace();
			event.getTarget().reply(event.getUser(),
					"Something broke. [" + e.getMessage() + "] :(");
		}
	}

	@Subscribe
	public void onFinishCommandFeatures(FinishCommandFeatures event) {
		try {
			List<String> messages = buildMessageMap("FEATURES",
					event.getMessageMap());
			URL url = longMessageSolution.provideSolution(messages,
					"Features as of " + new Date());
			event.getTarget().reply(event.getUser(), "Features: " + url);
		} catch (Exception e) {
			e.printStackTrace();
			event.getTarget().reply(event.getUser(),
					"Something broke. [" + e.getMessage() + "] :(");
		}
	}

	public void postCommands(Target target, User user) {
		final Multimap<String, String> messageMap = createTreeArrayListMultimap();

		CommandRollCall.Responder responder = new CommandRollCall.Responder() {
			@Override
			public void reply(String category, String command,
					String... description) {
				ImmutableList<String> formattedMessages = ImmutableList
						.<String> builder()
						.add(command)
						.addAll(Lists.transform(Arrays.asList(description),
								new Function<String, String>() {
									@Override
									public String apply(String input) {
										return "\t" + input;
									}
								})).add("\n").build();
				messageMap.putAll(category, formattedMessages);
			}
		};

		bus.post(new CommandRollCall(user, responder));
		bus.post(new FinishCommandCommands(messageMap, target, user));
	}

	public void postFeatures(Target target, User user) {
		final Multimap<String, String> messageMap = createTreeArrayListMultimap();

		FeatureRollCall.Responder responder = new FeatureRollCall.Responder() {
			@Override
			public void reply(String name, String... description) {
				ImmutableList<String> formattedMessages = ImmutableList
						.<String> builder()
						.addAll(Lists.transform(Arrays.asList(description),
								new Function<String, String>() {
									@Override
									public String apply(String input) {
										return "\t" + input;
									}
								})).build();
				messageMap.putAll(name, formattedMessages);
			}
		};

		bus.post(new FeatureRollCall(responder));
		bus.post(new FinishCommandFeatures(messageMap, target, user));
	}
}
