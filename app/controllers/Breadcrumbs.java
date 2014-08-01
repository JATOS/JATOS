package controllers;

import java.util.List;

import controllers.routes;
import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

public class Breadcrumbs extends Controller {

	public static final String COOKIE_EMAIL = "email";

	public static String getDashboardBreadcrumb() {
		return "<a href=\"" + routes.Dashboard.dashboard() + "\">" + "/" + "</a>";
	}

	public static String generateBreadcrumbs(String... crumbs) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < crumbs.length; i++) {
			sb.append(crumbs[i]);
			if (i < crumbs.length - 1) {
				sb.append(" > ");
			}
		}
		return sb.toString();
	}

	public static String getComponentBreadcrumb(StudyModel study,
			ComponentModel component) {
		StringBuffer sb = new StringBuffer();
		sb.append("<a href=\"");
		sb.append(routes.Components.index(study.getId(), component.getId()));
		sb.append("\">");
		sb.append(component.getTitle());
		sb.append("</a>");
		return sb.toString();
	}

	public static String getStudyBreadcrumb(StudyModel study) {
		StringBuffer sb = new StringBuffer();
		sb.append("<a href=\"");
		sb.append(routes.Studies.index(study.getId()));
		sb.append("\">");
		sb.append(study.getTitle());
		sb.append("</a>");
		return sb.toString();
	}
	
	public static String getUserBreadcrumb(UserModel user) {
		StringBuffer sb = new StringBuffer();
		sb.append("<a href=\"");
		sb.append(routes.Users.profile(user.getEmail()));
		sb.append("\">");
		sb.append(user.getName());
		sb.append(" (");
		sb.append(user.getEmail());
		sb.append(")");
		sb.append("</a>");
		return sb.toString();
	}
	
}
