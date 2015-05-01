package com.vectorcat.irc.demodbot.feature;

import java.io.IOException;
import java.net.URL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall.Responder;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.recv.IRCRecvCommand;
import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import com.wolfram.alpha.WASubpod;

@Singleton
public class WolframFeature {

	private final WAEngine engine;
	private final LongMessageSolution longMessageSolution;

	@Inject
	WolframFeature(HelpFeature helpFeature, EventBus bus,
			LongMessageSolution longMessageSolution) {
		this.longMessageSolution = longMessageSolution;

		bus.register(this);
		engine = initWolfram("87PU32-LVY7X74PJ7"); // FIXME for shame, myself.
	}

	private WAEngine initWolfram(String appID) {
		WAEngine engine = new WAEngine();

		engine.setAppID(appID);
		engine.addFormat("plaintext");

		return engine;
	}

	@Subscribe
	public void onCommand(final IRCRecvCommand event) {
		if (event.getCommand().equals("WOLFRAM")) {
			String input = event.getArguments().toString();

			WAQuery query = engine.createQuery(input);

			WAQueryResult result;
			try {
				result = engine.performQuery(query);

				if (result.isError()) {
					event.getTarget().reply(
							event.getUser(),
							"Something went wrong: " + result.getErrorCode()
									+ " - " + result.getErrorMessage());
				} else if (!result.isSuccess()) {
					event.getTarget().reply(event.getUser(),
							"I can't find anything for that query!");
				} else {
					Builder<String> message = ImmutableList.builder();

					for (WAPod pod : result.getPods()) {
						if (!pod.isError()) {
							if (pod.getSubpods().length == 1
									&& pod.getSubpods()[0].getContents().length == 1
									&& pod.getSubpods()[0].getContents()[0] instanceof WAPlainText) {
								message.add(pod.getTitle()
										+ ": "
										+ ((WAPlainText) pod.getSubpods()[0]
												.getContents()[0]).getText());
							} else {
								message.add(pod.getTitle() + ": ");
								for (WASubpod subpod : pod.getSubpods()) {
									for (Object element : subpod.getContents()) {
										if (element instanceof WAPlainText) {
											message.add("    "
													+ ((WAPlainText) element)
															.getText());
										}
									}
								}
							}
						}
					}

					ImmutableList<String> messageStrings = message.build();

					if (messageStrings.size() <= 5) {
						for (String messageLine : messageStrings) {
							event.getTarget().reply(event.getUser(),
									messageLine);
						}
					} else {
						URL url;
						try {
							url = longMessageSolution.provideSolution(
									messageStrings, "Wolfram|Alpha: " + input);
							event.getTarget().reply(event.getUser(),
									url.toString());
						} catch (IOException e) {
							event.getTarget().reply(event.getUser(),
									"Something broke: " + e.getMessage());
						}
					}
				}

			} catch (WAException e) {
				event.getTarget().reply(
						event.getUser(),
						"Something went wrong: " + e.getClass().getSimpleName()
								+ " - " + e.getMessage());
			}

		}
	}

	@Subscribe
	public void onCommandRollCall(CommandRollCall event) {
		Responder responder = event.getResponder();
		responder.reply("WOLFRAM", "!wolfram",
				"Queries Wolfram|Alpha using an API.");
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder().reply("Wolfram|Alpha",
				"Using the Wolfram API, serving queries!");
	}

}
