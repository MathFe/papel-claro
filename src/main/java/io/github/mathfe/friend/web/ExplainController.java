package io.github.mathfe.friend.web;

import java.io.IOException;

import io.github.mathfe.friend.explain.Answer;
import io.github.mathfe.friend.explain.DocumentExplainer;
import io.github.mathfe.friend.explain.DocumentExplanation;
import io.github.mathfe.friend.explain.DocumentInput;
import io.github.mathfe.friend.explain.InvalidInputException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ExplainController {

	private static final int MAX_QUESTION_LENGTH = 500;

	private final DocumentExplainer explainer;

	public ExplainController(DocumentExplainer explainer) {
		this.explainer = explainer;
	}

	@PostMapping(path = "/explain", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DocumentExplanation explain(
			@RequestParam(name = "text", required = false) String text,
			@RequestParam(name = "image", required = false) MultipartFile image) throws IOException {
		return this.explainer.explain(toInput(text, image));
	}

	@PostMapping(path = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Answer ask(
			@RequestParam(name = "text", required = false) String text,
			@RequestParam(name = "image", required = false) MultipartFile image,
			@RequestParam(name = "question", required = false) String question) throws IOException {
		String cleanQuestion = question == null ? "" : question.strip();
		if (cleanQuestion.isEmpty()) {
			throw new InvalidInputException("Escreva a sua pergunta.");
		}
		if (cleanQuestion.length() > MAX_QUESTION_LENGTH) {
			throw new InvalidInputException("A pergunta está muito grande. Tente resumir.");
		}
		return this.explainer.ask(toInput(text, image), cleanQuestion);
	}

	private static DocumentInput toInput(String text, MultipartFile image) throws IOException {
		boolean hasImage = image != null && !image.isEmpty();
		return DocumentInput.of(text, hasImage ? image.getBytes() : null, hasImage ? image.getContentType() : null);
	}
}
