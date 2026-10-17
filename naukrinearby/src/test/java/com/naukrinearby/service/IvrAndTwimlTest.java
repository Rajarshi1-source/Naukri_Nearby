package com.naukrinearby.service;

import com.naukrinearby.service.IvrService.Choice;
import com.naukrinearby.util.TwiML;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for IVR input parsing and TwiML escaping (no Spring context). */
class IvrAndTwimlTest {

	@Test
	void keypadDigits_drivetheMenu() {
		assertThat(IvrService.choice("1", null)).isEqualTo(Choice.APPLY);
		assertThat(IvrService.choice("2", null)).isEqualTo(Choice.NEARBY);
		assertThat(IvrService.choice("9", null)).isEqualTo(Choice.REPEAT);
		assertThat(IvrService.choice("5", null)).isEqualTo(Choice.UNKNOWN);
	}

	@Test
	void spokenHindiAndEnglish_mapToChoices() {
		assertThat(IvrService.choice(null, "Ek")).isEqualTo(Choice.APPLY);
		assertThat(IvrService.choice(null, "दो")).isEqualTo(Choice.NEARBY);
		assertThat(IvrService.choice(null, "haan ji")).isEqualTo(Choice.YES);
		assertThat(IvrService.choice(null, "नहीं")).isEqualTo(Choice.NO);
		assertThat(IvrService.choice(null, "something else")).isEqualTo(Choice.UNKNOWN);
	}

	@Test
	void jobNumbers_fromKeypadSpeechOrDevanagari() {
		assertThat(IvrService.extractDigits("1234#")).isEqualTo("1234");
		assertThat(IvrService.extractDigits("job 4 5 6")).isEqualTo("456");
		assertThat(IvrService.extractDigits("१२३")).isEqualTo("123");
		assertThat(IvrService.extractDigits("0042")).isEqualTo("42");
		assertThat(IvrService.extractDigits("9".repeat(50))).hasSize(12);
		assertThat(IvrService.extractDigits(null)).isEmpty();
	}

	@Test
	void twiml_escapesUserText_andKeepsDevanagari() {
		String xml = TwiML.response().message("<script>alert('x')</script> & \"hi\" नमस्ते").build();
		assertThat(xml)
				.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;hi&quot; नमस्ते")
				.doesNotContain("<script>")
				.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response>")
				.endsWith("</Response>");
	}

	@Test
	void twiml_gatherNestsPromptsAndEscapesAttributes() {
		String xml = TwiML.response()
				.gather("/voice/gather/menu?a=1&b=2", 1, "#", "hi-IN", "ek, do", g -> g.say("नमस्ते", "hi-IN"))
				.redirect("/voice")
				.build();
		assertThat(xml).contains("action=\"/voice/gather/menu?a=1&amp;b=2\"")
				.contains("numDigits=\"1\"")
				.contains("<Say voice=\"Polly.Aditi\" language=\"hi-IN\">नमस्ते</Say></Gather>")
				.contains("<Redirect method=\"POST\">/voice</Redirect>");
	}
}
