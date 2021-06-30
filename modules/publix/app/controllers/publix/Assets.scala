package controllers.publix

import play.api.http.HttpErrorHandler

import javax.inject.Inject

class Assets @Inject()(errorHandler: HttpErrorHandler,
                       assetsMetadata: controllers.AssetsMetadata
                      ) extends controllers.AssetsBuilder(errorHandler, assetsMetadata)
