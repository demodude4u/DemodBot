package com.vectorcat.irc.demodbot.feature;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;

import lombok.Data;
import lombok.EqualsAndHashCode;

import org.ocpsoft.prettytime.PrettyTime;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.Target;
import com.vectorcat.irc.User;
import com.vectorcat.irc.demodbot.event.AdminCommand;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall;
import com.vectorcat.irc.demodbot.event.help.CommandRollCall.Responder;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.recv.IRCRecvCommand;
import com.vectorcat.irc.event.recv.IRCRecvJoin;
import com.vectorcat.irc.event.recv.IRCRecvMessage;
import com.vectorcat.irc.event.recv.IRCRecvNickChange;
import com.vectorcat.irc.util.Arguments;

public class MailFeature {
	@Data
	@EqualsAndHashCode(exclude = { "message" })
	private static class Mail implements Comparable<Mail> {

		private final Target target;
		private final User from;
		private final User recipient;
		private final long timestamp;
		private final String message;

		@Override
		public int compareTo(Mail o) {
			return Long.compare(timestamp, o.timestamp);
		}

	}

	private static final String PERSISTANCEFILE = "mail.data";

	private static final String PERSISTANCEMAGIC = "BOTMAIL";

	private final IRCControl control;
	private final IRCState state;
	private final LongMessageSolution longMessageSolution;

	TreeMultimap<User, Mail> incoming = TreeMultimap.create();
	TreeMultimap<User, Mail> outgoing = TreeMultimap.create();
	Set<Mail> allMail = Sets.newLinkedHashSet();

	@Inject
	MailFeature(EventBus bus, IRCControl control, IRCState state,
			LongMessageSolution longMessageSolution) {
		this.control = control;
		this.state = state;
		this.longMessageSolution = longMessageSolution;
		bus.register(this);
		loadMailFromPersistance();
	}

	public void clearMail(Collection<Mail> mailList) {
		for (Mail mail : mailList) {
			incoming.get(mail.recipient).remove(mail);
			outgoing.get(mail.from).remove(mail);
			allMail.remove(mail);
		}
	}

	public void clearMail(Target target, User from) {
		for (Mail mail : outgoing.get(from)) {
			incoming.get(mail.getRecipient()).remove(mail);
			allMail.remove(mail);
		}
		outgoing.removeAll(from);

		target.reply(from, "All of your mail has been deleted.");
	}

	public void clearMail(Target target, final User from, User recipient) {

		ImmutableList<Mail> toDelete = ImmutableList.copyOf(Sets.filter(
				incoming.get(recipient), new Predicate<Mail>() {
					@Override
					public boolean apply(Mail input) {
						return input.getFrom().equals(from);
					}
				}));
		incoming.get(recipient).removeAll(toDelete);
		outgoing.get(from).removeAll(toDelete);
		allMail.removeAll(toDelete);

		target.reply(from, "All of your mail towards " + recipient
				+ " had been deleted.");
	}

	public List<Mail> getMailFor(Target target, User user) {
		ImmutableList.Builder<Mail> builder = ImmutableList.builder();
		for (Mail mail : incoming.get(user)) {
			if (target.isUser() && mail.getTarget().isUser()) {
				builder.add(mail);
			} else if (target.isChannel() && target.equals(mail.getTarget())) {
				builder.add(mail);
			}
		}
		return builder.build();
	}

	private void loadMailFromPersistance() {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				PERSISTANCEFILE))) {
			Preconditions.checkState(ois.readUTF().equals(PERSISTANCEMAGIC));
			int mailCount = ois.readInt();
			for (int i = 0; i < mailCount; i++) {
				Target target = control.getTarget(ois.readUTF());
				User from = control.getUser(ois.readUTF());
				User recipient = control.getUser(ois.readUTF());
				long timestamp = ois.readLong();
				String message = ois.readUTF();
				Mail mail = new Mail(target, from, recipient, timestamp,
						message);
				allMail.add(mail);
				incoming.get(mail.recipient).add(mail);
				outgoing.get(mail.from).add(mail);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean mail(Target target, User from, User recipient, String message) {
		Mail mail = new Mail(target, from, recipient,
				System.currentTimeMillis(), message);

		if (outgoing.get(from).size() >= 100) {
			target.reply(
					from,
					"You have too much outgoing mail! Use !clearmail [recipient] to empty your outbox.");
			return false;
		}

		if (incoming.get(recipient).size() >= 100) {
			target.reply(
					from,
					"The recipient has too much incoming mail!  If you have pending mail at "
							+ recipient
							+ " already and willing to delete it, use !clearmail "
							+ recipient);
			return false;
		}

		outgoing.put(from, mail);
		incoming.put(recipient, mail);
		allMail.add(mail);
		return true;
	}

	private String mailToString(PrettyTime p, Mail mail) {
		return "[From " + mail.getFrom() + ", "
				+ p.format(new Date(mail.getTimestamp())) + "] " + mail.message;
	}

	@Subscribe
	public void onAdminCommand(AdminCommand event) {
		if (event.getCommand().equals("CLEARALLMAIL")) {
			incoming.clear();
			outgoing.clear();
			allMail.clear();
			saveMailToPersistance();
			event.getTarget().reply(event.getUser(), "All mail deleted!");
		}
		if (event.getCommand().equals("MAILSTATS")) {
			event.getTarget().reply(
					event.getUser(),
					"Pending Mail: (" + incoming.size() + ") Recievers: ("
							+ incoming.keySet().size() + ") Senders: ("
							+ outgoing.keySet().size() + ")");
		}
		if (event.getCommand().equals("MAILINTEGRITY")) {
			event.getTarget().reply(
					event.getUser(),
					"Incoming: " + incoming.size() + " Outgoing: "
							+ outgoing.size());
		}

		if (event.getTarget().isUser()) {
			if (event.getCommand().equals("VIEWALLMAIL")) {
				URL url = viewAllMail();
				event.getUser().reply(event.getUser(), "" + url);
			}
			if (event.getCommand().equals("CLEARMAILTO")) {
				if (event.getArguments().size() == 1) {
					User recipient = control.getUser(event.getArguments()
							.get(0));
					ImmutableList<Mail> mailList = ImmutableList
							.copyOf(incoming.get(recipient));
					clearMail(mailList);
					saveMailToPersistance();
					event.getUser().reply(
							event.getUser(),
							"Cleared " + mailList.size() + " mail going to "
									+ recipient);
				}
			}
		}
	}

	@Subscribe
	public void onCommand(final IRCRecvCommand event) {
		if (event.getCommand().equals("MAIL")) {
			String[] split = event.getArguments().toString().split(":", 2);
			if (split.length <= 1) {
				event.getTarget().reply(event.getUser(),
						"Usage: !mail recipient1[ recipientN]* : message");
				return;
			}

			String message = split[1].trim();

			List<String> recipientStrings = ImmutableList.copyOf(new Arguments(
					split[0]));

			Set<User> mailedRecipients = Sets.newLinkedHashSet();
			for (String recipientString : recipientStrings) {
				User recipient = control.getUser(recipientString);
				if (mailedRecipients.contains(recipient)) {
					continue;
				}

				if (event.getUser().equals(recipient)) {
					event.getTarget().reply(event.getUser(),
							"Why are you trying to send mail to yourself?");
					continue;
				}
				if (state.getMyUser().equals(recipient)) {
					event.getTarget().reply(event.getUser(),
							"Why are you trying to send mail to me?");
					continue;
				}

				boolean mailed = mail(event.getTarget(), event.getUser(),
						recipient, message);
				if (mailed) {
					mailedRecipients.add(recipient);
				}
			}
			if (!mailedRecipients.isEmpty()) {
				event.getTarget().reply(event.getUser(),
						"Mail sent to " + mailedRecipients + "!");
				saveMailToPersistance();
			}
		}
		if (event.getCommand().equals("CLEARMAIL")) {
			if (event.getArguments().size() > 1) {
				event.getTarget().reply(event.getUser(),
						"Usage: !clearmail [recipient]");
				return;
			}

			if (event.getArguments().size() > 0) {
				User recipient = control.getUser(event.getArguments().get(0));

				clearMail(event.getTarget(), event.getUser(), recipient);
			} else {
				clearMail(event.getTarget(), event.getUser());
			}
			saveMailToPersistance();
		}
		if (event.getCommand().equals("VIEWMAIL")) {
			URL url = viewMail(event.getTarget(), event.getUser());
			event.getTarget().reply(event.getUser(),
					(url == null) ? "No mail!" : ("Outgoing mail: " + url));
		}
	}

	@Subscribe
	public void onCommandRollCall(CommandRollCall event) {
		Responder responder = event.getResponder();
		responder
				.reply("MAIL",
						"!mail recipient1[ recipientN]* : message",
						"Mails the specified recipients the message when they are found available.",
						"A user will be informed about their incoming mail when they first join ",
						"the channel, or talk within the channel.",//
						"",
						"If you send mail via private message to this bot, the mail will be sent ",
						"by private message to the recipients when they are found available ",
						"across all channels I service.",//
						"",
						"To prevent abuse, there is a limit on how much mail one user can make ",
						"or recieve.",//
						"",
						"If there is too much mail being sent to a user at once, I will shorten ",
						"my messages sent by sending a link to a paste of the messages instead.");
		responder
				.reply("MAIL",
						"!clearmail [recipient]",
						"If a recipient is specified, it will specifically delete incoming mail ",
						"to that recipient where you are the sender.", //
						"",
						"If no recipient was specified, all mail sent by you will be deleted.");
		responder
				.reply("MAIL",
						"!viewmail",
						"Links you to a paste of all mail yet to be recieved that was sent via ",
						"this channel.",//
						"",
						"To see all mail yet to be recieved sent by you, private message this bot ",
						"with this command.");
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Mail Feature",
						"This feature remembers messages and delivers them to users when they are ",
						"available.",//
						"",
						"If the user that you mailed is not found in any chat room, I will wait ",
						"till they join the channel to deliver the message.",//
						"",
						"If the user is already in the channel, I will wait to deliver the message ",
						"shortly after they start to speak.",//
						"",
						"There is also the ability to clear messages you have sent, in the case you ",
						"did not mean to send a message.");
	}

	@Subscribe
	public void onJoin(IRCRecvJoin event) {
		List<Mail> mailList = getMailFor(event.getChannel(), event.getUser());
		postMail(event.getChannel(), event.getUser(), mailList);
		clearMail(mailList);
	}

	@Subscribe
	public void onMessage(final IRCRecvMessage event) {
		final List<Mail> mailList = getMailFor(event.getTarget(),
				event.getUser());
		clearMail(mailList);
		if (!mailList.isEmpty()) {
			new Thread() {
				@Override
				public void run() {
					try {
						// I'm assuming they are talking to someone
						// So I want to give some time before my bot says
						// something
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}
					postMail(event.getTarget(), event.getUser(), mailList);
				}
			}.start();
		}
	}

	@Subscribe
	public void onNickChange(IRCRecvNickChange event) {
		final User user = event.getNewUser();
		new Thread() {// XXX I do this becuase I don't have a guarantee that
						// IRCState will update my channels before this method
						// is called
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				for (Channel channel : user.getChannels()) {
					List<Mail> mailList = getMailFor(channel, user);
					postMail(channel, user, mailList);
					clearMail(mailList);
				}
				List<Mail> mailList = getMailFor(user, user);
				postMail(user, user, mailList);
				clearMail(mailList);
				saveMailToPersistance();
			}
		}.start();
	}

	public void postMail(Target target, User user, List<Mail> mailList) {
		final PrettyTime p = new PrettyTime();
		if (mailList.size() < 5) {
			for (Mail mail : mailList) {
				target.reply(user, mailToString(p, mail));
			}
		} else {
			List<String> mailStrings = ImmutableList.copyOf(Lists.transform(
					mailList, new Function<Mail, String>() {
						@Override
						public String apply(Mail input) {
							return mailToString(p, input);
						}
					}));
			URL url;
			try {
				url = longMessageSolution.provideSolution(mailStrings,
						"Mail for " + user);
				target.reply(user, "You got " + mailList.size() + " mail! "
						+ url);
			} catch (IOException e) {
				target.reply(user, "You got " + mailList.size()
						+ " mail, but something broke. [" + e.getMessage()
						+ "] :(");
			}
		}
	}

	private void saveMailToPersistance() {
		try (ObjectOutputStream oos = new ObjectOutputStream(
				new FileOutputStream(PERSISTANCEFILE))) {
			oos.writeUTF(PERSISTANCEMAGIC);
			oos.writeInt(allMail.size());
			for (Mail mail : allMail) {
				oos.writeUTF(mail.target.toString());
				oos.writeUTF(mail.from.toString());
				oos.writeUTF(mail.recipient.toString());
				oos.writeLong(mail.timestamp);
				oos.writeUTF(mail.message);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private URL viewAllMail() {
		List<String> mailStrings = ImmutableList.copyOf(Collections2.transform(
				incoming.keySet(), new Function<User, String>() {
					PrettyTime p = new PrettyTime();

					@Override
					public String apply(User input) {
						StringBuilder builder = new StringBuilder();
						NavigableSet<Mail> mailSet = incoming.get(input);
						builder.append(input + " (" + mailSet.size() + "): \n");
						for (Mail mail : mailSet) {
							builder.append('\t')
									.append(mail.target.isUser() ? "Private"
											: mail.target.toString())
									.append(" -> ")
									.append(mailToString(p, mail)).append('\n');
						}
						return builder.toString();
					}
				}));
		try {
			return longMessageSolution.provideSolution(mailStrings, "All Mail");
		} catch (IOException e) {
			return null;
		}
	}

	private URL viewMail(Target target, User from) {
		StringBuilder builder = new StringBuilder();
		Set<Mail> mailSet = target.isChannel() ? allMail : outgoing.get(from);
		PrettyTime p = new PrettyTime();
		if (!mailSet.isEmpty()) {
			for (Mail mail : mailSet) {
				if (target.isChannel() && !target.equals(mail.target)) {
					continue;
				}
				builder.append(mail.recipient).append(": ")
						.append(mailToString(p, mail)).append('\n');
			}
		}
		List<String> mailStrings = ImmutableList.of(builder.toString());
		try {
			return longMessageSolution.provideSolution(
					mailStrings,
					"Outgoing mail from " + from + " -- Scope: "
							+ (target.isUser() ? "All" : target.toString()));
		} catch (IOException e) {
			return null;
		}
	}
}
