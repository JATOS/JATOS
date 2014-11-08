package services;

import java.util.LinkedHashMap;
import java.util.Map;

import play.mvc.Call;

public class Breadcrumbs {

	private Map<String, String> breadcrumbs = new LinkedHashMap<>();

	public Map<String, String> getBreadcrumbs() {
		return breadcrumbs;
	}

	public Breadcrumbs put(String name, String url) {
		this.breadcrumbs.put(name, url);
		return this;
	}
	
	public Breadcrumbs put(String name, Call call) {
		this.breadcrumbs.put(name, call.url());
		return this;
	}
	
}
