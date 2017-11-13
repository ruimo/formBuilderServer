import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.api.http.EnabledFilters
import play.filters.gzip.GzipFilter
import filters.LoginFilter

class Filters @Inject() (
  defaultFilters: EnabledFilters,
  gzip: GzipFilter,
  login: LoginFilter
) extends DefaultHttpFilters(defaultFilters.filters :+ gzip :+ login: _*)
