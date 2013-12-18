package com.vectorcat.irc.demodbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.commons.lang3.StringEscapeUtils;

public class GoogleSuggest {

	private static String extract(String content, String begin, String end) {
		int indexOfCorrection = content.indexOf(begin);
		int indexOfEnd = content.indexOf(end, indexOfCorrection);
		if (indexOfCorrection == -1) {
			return null;
		}
		String answer = content.substring(indexOfCorrection + begin.length(),
				indexOfEnd);
		return answer;
	}

	public String suggestSpelling(final String search)
			throws MalformedURLException, IOException {
		System.setProperty(
				"http.agent",
				"Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.0.7) Gecko/2009021910 Firefox/3.0.7");
		@SuppressWarnings("deprecation")
		URL url = new URL("http://www.google.com/search?hl=en&safe=off&q="
				+ URLEncoder.encode(search));

		InputStream is = url.openStream();

		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				is))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// System.out.println(line);
				builder.append(line + "\n");
			}
		}
		String content = builder.toString();
		String answer = extract(content, "<a class=\"spell\" href=\"/search?",
				"</a>");
		if (answer != null) {
			answer = answer.substring(answer.indexOf(">") + 1);
			answer = answer.replace("<b><i>", "");
			answer = answer.replace("</i></b>", "");
			answer = StringEscapeUtils.unescapeHtml4(answer);
		}
		return answer;
	}
}
