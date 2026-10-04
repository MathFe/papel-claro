package io.github.mathfe.friend.explain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring context and no Ollama needed. */
class ExplainRulesTest {

	@Test
	void rejectsWhenThereIsNoTextAndNoImage() {
		assertThatThrownBy(() -> DocumentInput.of("   ", null, null))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	void rejectsTextThatDoesNotFitTheContextWindow() {
		String huge = "a".repeat(DocumentInput.MAX_TEXT_LENGTH + 1);
		assertThatThrownBy(() -> DocumentInput.of(huge, null, null))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	void rejectsFilesThatAreNotImages() {
		assertThatThrownBy(() -> DocumentInput.of("", new byte[] { 1, 2, 3 }, "application/pdf"))
				.isInstanceOf(InvalidInputException.class);
	}

	@Test
	void acceptsTextOnly() {
		DocumentInput input = DocumentInput.of("  Contrato de aluguel  ", null, null);
		assertThat(input.text()).isEqualTo("Contrato de aluguel");
		assertThat(input.hasText()).isTrue();
		assertThat(input.hasImage()).isFalse();
	}

	@Test
	void acceptsPhotoOnly() {
		DocumentInput input = DocumentInput.of(null, new byte[] { 1, 2, 3 }, "IMAGE/JPEG");
		assertThat(input.hasText()).isFalse();
		assertThat(input.hasImage()).isTrue();
		assertThat(input.imageContentType()).isEqualTo("image/jpeg");
	}

	@Test
	void explanationFillsInWhatTheModelLeftOut() {
		DocumentExplanation explanation = new DocumentExplanation(null, null, null, null, null, null, null, "urgente");
		assertThat(explanation.documentType()).isEqualTo("Documento");
		assertThat(explanation.actions()).isEmpty();
		assertThat(explanation.glossary()).isEmpty();
		assertThat(explanation.urgency()).isEqualTo("MEDIA");
	}

	@Test
	void explanationKeepsKnownUrgencyLevels() {
		assertThat(DocumentExplanation.normalizeUrgency(" alta ")).isEqualTo("ALTA");
		assertThat(DocumentExplanation.normalizeUrgency("Baixa")).isEqualTo("BAIXA");
		assertThat(DocumentExplanation.normalizeUrgency("MÉDIA")).isEqualTo("MEDIA");
		assertThat(new DocumentExplanation("Boleto", "Resumo", List.of("Pagar"), null, null, null, null, "ALTA")
				.actions()).containsExactly("Pagar");
	}

	@Test
	void recognisesWhenOllamaIsOffline() {
		RuntimeException failure = new RuntimeException("I/O error", new ConnectException("Connection refused"));
		assertThat(DocumentExplainer.classify(failure)).isEqualTo(ModelException.Reason.OFFLINE);
	}

	@Test
	void recognisesWhenTheModelWasNotDownloaded() {
		RuntimeException failure = new RuntimeException("404 - model 'gemma3:4b' not found");
		assertThat(DocumentExplainer.classify(failure)).isEqualTo(ModelException.Reason.MODEL_MISSING);
	}

	@Test
	void anythingElseCountsAsAnUnreadableAnswer() {
		assertThat(DocumentExplainer.classify(new IllegalStateException("could not parse JSON")))
				.isEqualTo(ModelException.Reason.BAD_ANSWER);
	}
}
