package models.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for breadcrumbs used in UI but not persisted in DB
 *
 * @author Kristian Lange
 */
public class Breadcrumbs {

    public static class Breadcrumb {
        public final String name;
        public final String url;

        Breadcrumb(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private final List<Breadcrumb> breadcrumbs = new ArrayList<>();

    public void addBreadcrumb(String name, String url) {
        breadcrumbs.add(new Breadcrumb(name, url));
    }

    public List<Breadcrumb> getBreadcrumbs() {
        return breadcrumbs;
    }

}
