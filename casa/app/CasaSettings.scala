import controllers.Auth
import play.api.{Logger, Application, GlobalSettings}
import play.api.mvc._
import scala.Some
import play.Play
import utils.Injector
import monitor.MonitorFilter
import monitoring._
import monitoring.CasaMonitorRegistration

class CasaSettings extends WithFilters(MonitorFilter) with Injector with CasaMonitorRegistration with GlobalSettings {

  this: Injector =>

  lazy val  authController = resolve(classOf[Auth])

  override def onStart(app: Application): Unit = {
    Logger.info("SA is now starting")

    registerReporters()
  }

  override def onStop(app: Application): Unit = {
    Logger.info("SA is now stopping")
  }



  /**
   * Intercept requests to check for session timeout
   * @param request the incoming request
   * @return
   */
  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    val timeout = Play.application().configuration().getString("application.session.maxAge").toLong

    // could also filter out bad request; also find a smarter way to test contains
    if(request.path.contains("assets") || request.path.contains("login")||request.path.contains("logout") ||request.path.contains("password") )
      super.onRouteRequest(request)
    else {
      request.session.get("currentTime") match {
        case Some(time) =>
          // difference in ms
          val deltaMs = (System.nanoTime() - time.toLong)/1000000
          // if less than 30min ok, else session timeout
          if(deltaMs < timeout*60*1000)  {
            super.onRouteRequest(request)
          }
          else Some(authController.login)
        case _ => Some(authController.login)
      }
    }
  }

  override def getControllerInstance[A](controllerClass: Class[A]): A = resolve(controllerClass)

}
