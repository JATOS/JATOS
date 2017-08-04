package general

import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.headers.SecurityHeadersFilter

/**
  * Increases security
  * (https://www.playframework.com/documentation/2.5.x/SecurityHeaders)
  */
class Filters @Inject() (securityHeadersFilter: SecurityHeadersFilter)
  extends DefaultHttpFilters(securityHeadersFilter)
