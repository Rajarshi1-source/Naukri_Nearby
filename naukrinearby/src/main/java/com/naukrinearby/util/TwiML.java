package com.naukrinearby.util;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;

/**
 * Minimal TwiML builder shared by the WhatsApp reply and the IVR (ADR 0008). Every piece of text and
 * every attribute value is escaped with {@code HtmlUtils.htmlEscape(value, "UTF-8")}, which for UTF-8
 * escapes exactly {@code & < > " '} — valid XML, and Devanagari passes through unchanged. Never build
 * TwiML by concatenating user input.
 */
public final class TwiML {

	/**
	 * TwiML is UTF-8 XML. The charset must be explicit: without it the body is written and read as
	 * ISO-8859-1, which garbles every Hindi prompt.
	 */
	public static final MediaType CONTENT_TYPE = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);

	/** Amazon Polly's Indian voice; it speaks both Hindi (hi-IN) and Indian English (en-IN). */
	public static final String VOICE = "Polly.Aditi";

	private final StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>");

	private TwiML() {
	}

	public static TwiML response() {
		return new TwiML();
	}

	public TwiML message(String text) {
		xml.append("<Message>").append(escape(text)).append("</Message>");
		return this;
	}

	public TwiML say(String text, String language) {
		xml.append("<Say voice=\"").append(VOICE).append("\" language=\"").append(escape(language)).append("\">")
				.append(escape(text)).append("</Say>");
		return this;
	}

	/**
	 * {@code <Gather>} collecting DTMF and speech, posting the result to {@code action}.
	 *
	 * @param numDigits digits to collect before submitting, or 0 to wait for {@code finishOnKey}
	 */
	public TwiML gather(String action, int numDigits, String finishOnKey, String speechLanguage, String hints,
			Consumer<TwiML> prompts) {
		xml.append("<Gather input=\"dtmf speech\" method=\"POST\" timeout=\"6\" action=\"").append(escape(action)).append('"');
		if (numDigits > 0) {
			xml.append(" numDigits=\"").append(numDigits).append('"');
		}
		if (finishOnKey != null) {
			xml.append(" finishOnKey=\"").append(escape(finishOnKey)).append('"');
		}
		xml.append(" language=\"").append(escape(speechLanguage)).append('"');
		if (hints != null && !hints.isBlank()) {
			xml.append(" hints=\"").append(escape(hints)).append('"');
		}
		xml.append('>');
		prompts.accept(this);
		xml.append("</Gather>");
		return this;
	}

	public TwiML redirect(String url) {
		xml.append("<Redirect method=\"POST\">").append(escape(url)).append("</Redirect>");
		return this;
	}

	public TwiML hangup() {
		xml.append("<Hangup/>");
		return this;
	}

	public String build() {
		return xml + "</Response>";
	}

	public static String escape(String s) {
		return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
	}
}
