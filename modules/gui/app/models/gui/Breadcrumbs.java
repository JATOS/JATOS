package models.gui;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.Json;

/**
 * Model for breadcrumbs used in UI but not persisted in DB
 * 
 * @author Kristian Lange
 */
public class Breadcrumbs {

	public static class Breadcrumb {
		public String name;
		public String url;

		Breadcrumb(String name, String url) {
			this.name = name;
			this.url = url;
		}
	}

	private List<Breadcrumb> breadcrumbs = new ArrayList<>();

	public void addBreadcrumb(String name, String url) {
		breadcrumbs.add(new Breadcrumb(name, url));
	}

	public List<Breadcrumb> getBreadcrumbs() {
		return breadcrumbs;
	}

	public String asJson() throws JsonProcessingException {
		ArrayNode arrayNode = Json.mapper().createArrayNode();
		for (Breadcrumb breadcrumb : getBreadcrumbs()) {
			ObjectNode node = Json.mapper().createObjectNode();
			node.put("name", breadcrumb.name);
			node.put("url", breadcrumb.url);
			arrayNode.add(node);
		}
		return Json.mapper().writeValueAsString(arrayNode);
	}

}
