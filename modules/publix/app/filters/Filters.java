package filters;

import play.http.DefaultHttpFilters;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Filters extends DefaultHttpFilters {

    @Inject
    public Filters(IdCookieFilter idCookieFilter) {
        super(idCookieFilter);
    }

}