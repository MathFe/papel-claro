package io.github.mathfe.friend.explain;

/** Answer to a follow-up question about the document. */
public record Answer(String answer, Boolean foundInDocument) {

	public Answer {
		answer = answer == null ? "" : answer.trim();
		foundInDocument = foundInDocument == null ? Boolean.TRUE : foundInDocument;
	}
}
