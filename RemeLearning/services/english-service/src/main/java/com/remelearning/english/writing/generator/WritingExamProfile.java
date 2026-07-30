package com.remelearning.english.writing.generator;

import com.remelearning.common.constants.ExamTypes;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What an exam style means concretely when generating a writing/translation task: how long the source
 * passage should be, what it should be about, and how formal it should read.
 *
 * <p>This exists because "TOEIC" and "IELTS" are not interchangeable labels to paste into a prompt -
 * they imply genuinely different tasks. A TOEIC translation is a short, practical workplace text; an
 * IELTS one is a longer, more academic passage. Without this, every generated passage came out the
 * same length and roughly the same register regardless of what the learner was preparing for.
 *
 * <p>Sentence count is drawn randomly from the profile's range per generation, so a learner doing ten
 * TOEIC translations gets ten different lengths rather than the same shape every time.
 */
public enum WritingExamProfile {

	/** Workplace/commercial English: short, practical, transactional. */
	TOEIC(
			2, 4,
			List.of(
					"a workplace email confirming or changing a meeting",
					"an internal announcement about an office move or policy change",
					"a purchase order or delivery/shipping issue",
					"a customer enquiry and the reply to it",
					"a short report on monthly sales or staff training",
					"a business trip itinerary or expense claim",
					"a job advertisement or interview scheduling note",
					"a notice about building maintenance or equipment failure"),
			"practical and businesslike, the way real office correspondence reads - no literary flourishes",
			List.of(
					"an email to a colleague or client",
					"a short internal announcement",
					"a reply to a customer complaint",
					"a brief report to a manager")),

	/** Academic/social-issue English: longer, more formal, argument-oriented. */
	IELTS(
			4, 6,
			List.of(
					"the effect of technology on how people study or work",
					"urban growth, traffic and public transport",
					"environmental protection and everyday habits",
					"education policy and access to schooling",
					"public health, diet and exercise",
					"the role of government versus individuals in solving a problem",
					"changes in family life across generations",
					"tourism and its effect on local communities"),
			"more formal and academic, the register of an IELTS Writing passage - full clauses, precise linking words",
			List.of(
					"an opinion essay taking one clear position (IELTS Task 2 style)",
					"a discussion essay weighing both views before concluding (IELTS Task 2 style)",
					"a problem-and-solution essay (IELTS Task 2 style)",
					"a description of a trend or comparison of data (IELTS Task 1 style)")),

	/** Academic but campus-oriented; slightly shorter than IELTS. */
	TOEFL(
			3, 5,
			List.of(
					"a campus announcement and a student's reaction to it",
					"a lecture summary on a natural-science topic",
					"choosing between two study or living options",
					"the value of group work versus studying alone",
					"library, laboratory or dormitory facilities",
					"balancing part-time work with study"),
			"academic but conversational-academic, the register of a TOEFL integrated task",
			List.of(
					"an independent essay agreeing or disagreeing with a statement",
					"a summary comparing a reading and a lecture",
					"an email to a professor or university office")),

	/** Vietnam's standardized test: everyday + semi-formal Vietnamese-context topics. */
	VSTEP(
			3, 5,
			List.of(
					"studying and taking exams in Vietnam",
					"traffic and daily commuting in a Vietnamese city",
					"local festivals, food and customs",
					"choosing a career after graduation",
					"using social media responsibly",
					"protecting the environment in your neighbourhood"),
			"semi-formal, the register of a VSTEP writing task - clear and correct rather than ornate",
			List.of(
					"a letter or email responding to a given situation (VSTEP Task 1 style)",
					"an essay presenting your opinion with reasons (VSTEP Task 2 style)")),

	/** No exam in mind: everyday English. */
	GENERAL(
			3, 5,
			List.of(
					"a memorable day at work or school",
					"a place you have lived in or visited",
					"a habit you are trying to build or break",
					"a person who influenced you",
					"how you spend your weekends",
					"a small problem you solved recently"),
			"natural everyday English, neither formal nor slangy",
			List.of(
					"a short personal narrative",
					"a description of a place or person",
					"a paragraph explaining your opinion on an everyday topic"));

	private final int minSentences;
	private final int maxSentences;
	private final List<String> topics;
	private final String registerHint;
	private final List<String> composeFormats;

	WritingExamProfile(
			int minSentences, int maxSentences, List<String> topics, String registerHint, List<String> composeFormats) {
		this.minSentences = minSentences;
		this.maxSentences = maxSentences;
		this.topics = topics;
		this.registerHint = registerHint;
		this.composeFormats = composeFormats;
	}

	/**
	 * Profile for a caller-supplied exam type; {@link #GENERAL} for null/blank/unrecognised, so an
	 * exam style the frontend adds before the backend knows about it still produces a usable task
	 * instead of failing.
	 */
	public static WritingExamProfile fromExamType(String examType) {
		String normalized = ExamTypes.normalize(examType);
		if (normalized == null) {
			return GENERAL;
		}
		for (WritingExamProfile profile : values()) {
			if (profile.name().equalsIgnoreCase(normalized)) {
				return profile;
			}
		}
		return GENERAL;
	}

	/** A fresh random length inside this profile's range, so repeated generations vary. */
	public int randomSentenceCount() {
		return ThreadLocalRandom.current().nextInt(minSentences, maxSentences + 1);
	}

	/** One random topic suggestion; the generator uses it only when it has no weak-point labels to build around. */
	public String randomTopic() {
		return topics.get(ThreadLocalRandom.current().nextInt(topics.size()));
	}

	/** One random writing format for a COMPOSE task, so the brief isn't always the same kind of text. */
	public String randomComposeFormat() {
		return composeFormats.get(ThreadLocalRandom.current().nextInt(composeFormats.size()));
	}

	public String registerHint() {
		return registerHint;
	}

	public int minSentences() {
		return minSentences;
	}

	public int maxSentences() {
		return maxSentences;
	}
}
