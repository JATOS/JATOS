package controllers

import play.api.mvc._

import javax.inject.{Inject, Singleton}

@Singleton
class Ping @Inject()(components: ControllerComponents) extends AbstractController(components) {

  def ping = Action {
    Ok("pong")
  }

}