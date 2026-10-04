package io.github.mathfe.friend.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

	private final String modelName;

	public PageController(@Value("${app.model}") String modelName) {
		this.modelName = modelName;
	}

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("modelName", this.modelName);
		return "index";
	}
}
