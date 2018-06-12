package controllers.publix

import javax.inject.Inject

import controllers.Assets.Asset
import play.api.mvc.{Action, AnyContent}

class Assets @Inject()(assets: controllers.Assets) {

  def versioned(path: String, file: Asset): Action[AnyContent] = {
    assets.versioned(path, file)
  }

  def at(path: String, file: String): Action[AnyContent] = {
    assets.at(path, file)
  }

}
