package general;

import javax.inject.Inject;

import play.filters.headers.SecurityHeadersFilter;
import play.http.DefaultHttpFilters;

/**
 * Increases security
 * (https://www.playframework.com/documentation/2.5.x/SecurityHeaders)
 */
public class Filters extends DefaultHttpFilters {

	@Inject
	public Filters(SecurityHeadersFilter securityHeadersFilter) {
		super(securityHeadersFilter);
	}
}
