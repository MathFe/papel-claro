(function () {
	'use strict';

	var $ = function (id) { return document.getElementById(id); };

	var docForm = $('doc-form');
	var docText = $('doc-text');
	var docImage = $('doc-image');
	var photoBox = $('photo');
	var photoPreview = $('photo-preview');
	var explainButton = $('explain-button');
	var statusBox = $('status');
	var errorBox = $('error');
	var result = $('result');
	var askForm = $('ask-form');
	var askButton = $('ask-button');
	var questionInput = $('question');
	var answers = $('answers');
	var listenButton = $('listen-button');

	// The photo, already shrunk, ready to be sent. Null when there is none.
	var photoBlob = null;
	// What was sent for the current explanation, so follow-up questions use the same document.
	var current = null;
	var spokenText = '';

	var EXAMPLE = [
		'BANCO EXEMPLO S.A. (documento fictício para demonstração)',
		'',
		'NOTIFICAÇÃO EXTRAJUDICIAL',
		'',
		'Prezado(a) cliente,',
		'',
		'Informamos que consta em aberto a parcela nº 07/24 do Contrato de Crédito Pessoal nº 000123, ' +
		'vencida em 10/09/2026, no valor de R$ 412,90. Sobre o valor em atraso incidem multa moratória ' +
		'de 2% e juros de mora de 1% ao mês, pro rata die, além de encargos previstos na cláusula 8ª.',
		'',
		'Solicitamos a regularização do débito até 20/10/2026. O não pagamento no prazo poderá ensejar ' +
		'a inclusão do seu nome nos órgãos de proteção ao crédito e o vencimento antecipado das parcelas ' +
		'vincendas, sem prejuízo das medidas judiciais cabíveis.',
		'',
		'Caso o pagamento já tenha sido efetuado, desconsidere esta notificação.',
		'Atendimento: 0800 000 0000.'
	].join('\n');

	function show(element, visible) { element.hidden = !visible; }

	function setStatus(message) {
		statusBox.textContent = message || '';
		show(statusBox, Boolean(message));
	}

	function setError(message) {
		errorBox.textContent = message || '';
		show(errorBox, Boolean(message));
	}

	// Phone photos are large. Shrinking them in the browser makes the upload small
	// and gives the model an image it can read quickly.
	function shrinkImage(file) {
		return new Promise(function (resolve, reject) {
			var url = URL.createObjectURL(file);
			var image = new Image();
			image.onload = function () {
				var maxSide = 1600;
				var scale = Math.min(1, maxSide / Math.max(image.width, image.height));
				var canvas = document.createElement('canvas');
				canvas.width = Math.round(image.width * scale);
				canvas.height = Math.round(image.height * scale);
				canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
				URL.revokeObjectURL(url);
				canvas.toBlob(function (blob) {
					if (blob) { resolve(blob); } else { reject(new Error('toBlob')); }
				}, 'image/jpeg', 0.85);
			};
			image.onerror = function () {
				URL.revokeObjectURL(url);
				reject(new Error('image'));
			};
			image.src = url;
		});
	}

	function clearPhoto() {
		photoBlob = null;
		docImage.value = '';
		if (photoPreview.src) { URL.revokeObjectURL(photoPreview.src); }
		photoPreview.removeAttribute('src');
		show(photoBox, false);
	}

	docImage.addEventListener('change', function () {
		var file = docImage.files && docImage.files[0];
		if (!file) { return; }
		setError('');
		shrinkImage(file).then(function (blob) {
			photoBlob = blob;
			if (photoPreview.src) { URL.revokeObjectURL(photoPreview.src); }
			photoPreview.src = URL.createObjectURL(blob);
			show(photoBox, true);
		}).catch(function () {
			clearPhoto();
			setError('Não consegui abrir essa foto. Tente outra, em JPG ou PNG.');
		});
	});

	$('photo-remove').addEventListener('click', clearPhoto);

	$('example-button').addEventListener('click', function () {
		docText.value = EXAMPLE;
		docText.focus();
	});

	function buildForm(source, question) {
		var form = new FormData();
		if (source.text) { form.append('text', source.text); }
		if (source.photo) { form.append('image', source.photo, 'documento.jpg'); }
		if (question) { form.append('question', question); }
		return form;
	}

	function post(path, form) {
		return fetch(path, { method: 'POST', body: form }).then(function (response) {
			return response.json().catch(function () { return {}; }).then(function (body) {
				if (!response.ok) {
					throw new Error(body.error || 'Algo deu errado. Tente de novo.');
				}
				return body;
			});
		}, function () {
			throw new Error('Não consegui falar com o programa. Ele ainda está aberto neste computador?');
		});
	}

	function fillList(blockId, listId, items, makeItem) {
		var list = $(listId);
		list.textContent = '';
		(items || []).forEach(function (item) {
			var node = makeItem(item);
			if (node) { list.appendChild(node); }
		});
		show($(blockId), list.children.length > 0);
	}

	function textItem(text) {
		if (!text) { return null; }
		var li = document.createElement('li');
		li.textContent = text;
		return li;
	}

	function pairItem(strongText, restText) {
		if (!strongText && !restText) { return null; }
		var li = document.createElement('li');
		var strong = document.createElement('strong');
		strong.textContent = strongText || '';
		var span = document.createElement('span');
		span.textContent = restText || '';
		li.appendChild(strong);
		li.appendChild(span);
		return li;
	}

	var URGENCY_LABEL = { BAIXA: 'Sem pressa', MEDIA: 'Atenção ao prazo', ALTA: 'Urgente' };

	function render(data) {
		$('doc-type').textContent = data.documentType || 'Documento';

		var urgency = (data.urgency || 'MEDIA').toUpperCase();
		var urgencyBox = $('urgency');
		urgencyBox.textContent = URGENCY_LABEL[urgency] || URGENCY_LABEL.MEDIA;
		urgencyBox.className = 'urgency ' + urgency.toLowerCase();

		$('summary').textContent = data.summary || 'Não consegui resumir este documento.';

		fillList('actions-block', 'actions', data.actions, textItem);
		fillList('deadlines-block', 'deadlines', data.deadlines, function (d) {
			return pairItem(d && d.date, d && d.description);
		});
		fillList('amounts-block', 'amounts', data.amounts, function (a) {
			return pairItem(a && a.value, a && a.description);
		});
		fillList('warnings-block', 'warnings', data.warnings, textItem);

		var glossary = $('glossary');
		glossary.textContent = '';
		(data.glossary || []).forEach(function (entry) {
			if (!entry || !entry.term) { return; }
			var dt = document.createElement('dt');
			dt.textContent = entry.term;
			var dd = document.createElement('dd');
			dd.textContent = entry.meaning || '';
			glossary.appendChild(dt);
			glossary.appendChild(dd);
		});
		show($('glossary-block'), glossary.children.length > 0);

		spokenText = [data.summary]
			.concat((data.actions || []).length ? ['O que você precisa fazer:'].concat(data.actions) : [])
			.concat((data.warnings || []).length ? ['Preste atenção:'].concat(data.warnings) : [])
			.filter(Boolean)
			.join(' ');

		answers.textContent = '';
		questionInput.value = '';
		show(result, true);
		result.scrollIntoView({ behavior: 'smooth', block: 'start' });
	}

	docForm.addEventListener('submit', function (event) {
		event.preventDefault();
		var source = { text: docText.value.trim(), photo: photoBlob };
		if (!source.text && !source.photo) {
			setError('Cole o texto do documento ou envie uma foto dele.');
			return;
		}
		stopSpeaking();
		setError('');
		show(result, false);
		setStatus('Lendo o documento com calma. Isso pode levar alguns segundos...');
		explainButton.disabled = true;

		post('/api/explain', buildForm(source)).then(function (data) {
			current = source;
			render(data);
		}).catch(function (error) {
			setError(error.message);
		}).then(function () {
			setStatus('');
			explainButton.disabled = false;
		});
	});

	askForm.addEventListener('submit', function (event) {
		event.preventDefault();
		var question = questionInput.value.trim();
		if (!question || !current) { return; }
		setError('');
		askButton.disabled = true;

		var box = document.createElement('div');
		box.className = 'qa';
		var q = document.createElement('p');
		q.className = 'q';
		q.textContent = question;
		var a = document.createElement('p');
		a.className = 'a';
		a.textContent = 'Procurando no documento...';
		box.appendChild(q);
		box.appendChild(a);
		answers.appendChild(box);

		post('/api/ask', buildForm(current, question)).then(function (data) {
			a.textContent = data.answer || 'Não encontrei essa resposta no documento.';
			if (data.foundInDocument === false) {
				var note = document.createElement('p');
				note.className = 'not-found';
				note.textContent = 'O documento não fala sobre isso diretamente.';
				box.appendChild(note);
			}
			questionInput.value = '';
		}).catch(function (error) {
			a.textContent = error.message;
		}).then(function () {
			askButton.disabled = false;
		});
	});

	// Reading aloud. Some browser voices send the text to a server, which would break
	// the privacy promise, so only voices installed on the device (localService) are used.
	// If the device has no local Portuguese voice, the button is not shown.
	var canSpeak = 'speechSynthesis' in window;

	function localVoice() {
		if (!canSpeak) { return null; }
		var voices = window.speechSynthesis.getVoices().filter(function (voice) {
			return voice.localService && /^pt/i.test(voice.lang);
		});
		var brazilian = voices.filter(function (voice) { return /^pt[-_]BR/i.test(voice.lang); });
		return brazilian[0] || voices[0] || null;
	}

	function stopSpeaking() {
		if (canSpeak) { window.speechSynthesis.cancel(); }
		listenButton.textContent = 'Ouvir a explicação';
	}

	function refreshListenButton() { show(listenButton, Boolean(localVoice())); }

	refreshListenButton();
	if (canSpeak && 'onvoiceschanged' in window.speechSynthesis) {
		window.speechSynthesis.onvoiceschanged = refreshListenButton;
	}

	listenButton.addEventListener('click', function () {
		if (window.speechSynthesis.speaking) {
			stopSpeaking();
			return;
		}
		var voice = localVoice();
		if (!spokenText || !voice) { return; }
		var speech = new SpeechSynthesisUtterance(spokenText);
		speech.voice = voice;
		speech.lang = voice.lang;
		speech.rate = 0.95;
		speech.onend = stopSpeaking;
		speech.onerror = stopSpeaking;
		listenButton.textContent = 'Parar de ler';
		window.speechSynthesis.speak(speech);
	});
})();
