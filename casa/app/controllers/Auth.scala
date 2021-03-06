package controllers

import javax.inject.Inject

import play.api.Play._
import play.api.http.HeaderNames._
import play.api.i18n.{MessagesApi, I18nSupport}
import play.api.mvc._
import services.{AccessControlService, PasswordService}
import play.api.data._
import play.api.data.Forms._
import views.html
import scala.Predef._
import play.api.Logger
import scala.util.{Failure, Success, Try}
import app.ConfigProperties._
import utils.ApplicationUtils
import utils.JsValueWrapper.improveJsValue
import scala.language.implicitConversions

class Auth @Inject() (accessControlService: AccessControlService) extends Controller with I18nSupport {
  override def messagesApi: MessagesApi = current.injector.instanceOf[MessagesApi]
  val loginForm = Form(
    tuple(
      "userId" -> text,
      "password" -> text
    ) verifying("Staff ID should be numerals only and 8 characters long.",
      result => result match {case (userId, password) => validateUserId(userId)}
    )
      verifying ("Invalid user id or password",
      result => result match {case (userId, password) => checkUser(userId, password)}
    )
  )

  def validateUserId(userId:String) = {
    val restrictedStringPattern = """^[0-9]{8}$""".r
    restrictedStringPattern.pattern.matcher(userId).matches
  }

  def checkUser(userId: String, inputPassword: String): Boolean = {
      val userJson = accessControlService.findByUserId(userId)
      val password = (userJson \ "password").get.as[String]
      if(password.length() > 4) {
        if (PasswordService.checkPassword(inputPassword, password.toString)) true
        else false
      }
      else false
  }

  def getOriginTag(userId: String): String = {
    val userJson = accessControlService.findByUserId(userId)
    (userJson \ "originTag").get.as[String]
  }

  def checkPassword(userId: String): Boolean = {
    val userJson = accessControlService.getDaysToExpiration(userId)
    if(userJson.toString().equalsIgnoreCase("false")) false
    else {
      val days = userJson.as[Int]
      if(days <= 0) false
      else true
    }
  }

  /**
   * Login page.
   */
  def login =
    Action { implicit request =>
      Ok(html.login(loginForm))
        .withHeaders(CACHE_CONTROL -> "no-cache, no-store", "X-Frame-Options" -> "SAMEORIGIN") // stop click jacking)
        .withCookies(request.cookies.toSeq.filterNot( _.name == "CASAVersion") :+ Cookie("CASAVersion", "2.1"): _*)
    }


  /**
   * Handle login form submission.
   */
  def authenticate = Action { implicit request =>
    try {
      loginForm.bindFromRequest.fold(
        formWithErrors =>
          BadRequest(html.login(formWithErrors)),
        user =>
          Redirect(routes.Application.index)
          .withSession("userId" -> user._1, "originTag" -> getOriginTag(user._1), "days"->accessControlService.getDaysToExpiration(user._1).toString(), "currentTime"->System.nanoTime().toString)
          .withHeaders(CACHE_CONTROL -> "no-cache, no-store", "X-Frame-Options" -> "SAMEORIGIN") // stop click jacking
      )
    } catch {
      case e: Exception =>
        Logger.error(s"Could not connect to the access service",e)
        Ok(views.html.common.error(ApplicationUtils.startPage, "Access control connection error"))
          .withHeaders(CACHE_CONTROL -> "no-cache, no-store", "X-Frame-Options" -> "SAMEORIGIN") // stop click jacking
    }
  }

  /**
   * Logout and clean the session.
   */
  def logout = Action {
    Redirect(controllers.routes.Auth.login).discardingCookies(DiscardingCookie(getStringProperty("play.filters.csrf.cookie.name"), secure= getBooleanProperty("play.filters.csrf.cookie.secure")))
      .withNewSession.flashing("success" -> "You've been logged out")
  }
}

/**
 * Provide security features
 */
trait Secured {
  import play.api.mvc.Results._

  /**
   * Retrieve the connected user id.
   */
  private def username(request: RequestHeader) = {
    request.session.get("userId")
  }

  protected def getOriginTag(request: RequestHeader) = {
    request.session.get("originTag").getOrElse("GB")
  }
  /**
   * Redirect to login if the user is not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = {
    Results.Redirect(routes.Auth.login)
  }


  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = Security.Authenticated(username, onUnauthorized) { user =>
    Action { implicit request =>

      withSecureHeaders(Try(
        f(user)(request).withSession("userId"->user, "originTag" -> getOriginTag(request), "days"-> request.session.get("days").getOrElse(""), "currentTime"->System.nanoTime().toString)
      ) match {
        case Success(s) => s
        case Failure(e) =>
          val errorMsg = request.path match {
            case s if s.startsWith("/render") => "Could not connect to the render service"
            case _ => "Unexpected error in action authentication wrapper" //something happened on executing "f". The error is not really here, check source in stacktrace
          }
          Logger.error(errorMsg,e)
          Ok(views.html.common.error(ApplicationUtils.startPage, errorMsg))
      })

    }
  }

  protected def withSecureHeaders(result:Result): Result = {
    result.withHeaders(CACHE_CONTROL -> "no-cache, no-store", "X-Frame-Options" -> "SAMEORIGIN")
  }

}