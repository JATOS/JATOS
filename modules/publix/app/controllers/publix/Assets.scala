package controllers.publix

import javax.inject.Inject
import play.api.http.HttpErrorHandler

class Assets @Inject()(errorHandler: HttpErrorHandler,
                       assetsMetadata: controllers.AssetsMetadata
                      ) extends controllers.AssetsBuilder(errorHandler, assetsMetadata)
