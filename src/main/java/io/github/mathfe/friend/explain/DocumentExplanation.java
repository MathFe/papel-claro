package io.github.mathfe.friend.explain;

import java.util.List;

/**
 * What the model gives back for a document: a plain-language reading of it.
 * Field names are in English, the values come back in Brazilian Portuguese.
 */
public record DocumentExplanation(
		String documentType,
		String summary,
		List<String> actions,
		List<Deadline> deadlines,
		List<Amount> amounts,
		List<String> warnings,
		List<Term> glossary,
		String urgency) {

	public record Deadline(String date, String description) {
	}

	public record Amount(String value, String description) {
	}

	public record Term(String term, String meaning) {
	}

	/**
	 * A small local model sometimes leaves a field out. Normalising here means the
	 * screen never has to deal with nulls or an urgency level it does not know.
	 */
	public DocumentExplanation {
		documentType = blankTo(documentType, "Documento");
		summary = blankTo(summary, "");
		actions = actions == null ? List.of() : List.copyOf(actions);
		deadlines = deadlines == null ? List.of() : List.copyOf(deadlines);
		amounts = amounts == null ? List.of() : List.copyOf(amounts);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
		glossary = glossary == null ? List.of() : List.copyOf(glossary);
		urgency = normalizeUrgency(urgency);
	}

	static String normalizeUrgency(String raw) {
		if (raw == null) {
			return "MEDIA";
		}
		String value = raw.trim().toUpperCase().replace('É', 'E');
		return switch (value) {
			case "BAIXA", "ALTA" -> value;
			default -> "MEDIA";
		};
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
