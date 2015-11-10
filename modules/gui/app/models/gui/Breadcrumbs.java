package models.gui;

import java.util.ArrayList;
import java.util.List;

import utils.common.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
	
	public String asJson()
			throws JsonProcessingException {
		ArrayNode arrayNode = JsonUtils.OBJECTMAPPER.createArrayNode();
		for (Breadcrumb breadcrumb : getBreadcrumbs()) {
			ObjectNode node = JsonUtils.OBJECTMAPPER.createObjectNode();
			node.put("name", breadcrumb.name);
			node.put("url", breadcrumb.url);
			arrayNode.add(node);
		}
		return JsonUtils.OBJECTMAPPER.writeValueAsString(arrayNode);
	}

}
