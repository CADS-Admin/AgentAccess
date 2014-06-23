import controllers.Auth
import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._

class ApplicationSpec extends Specification {
  val userInput = Seq("userId"-> "test", "password"-> "john")

  "Application" should {

    "send bad requests to the login page" in new WithApplication{
      val bad = route(FakeRequest(GET, "/boum")).get

      status(bad) must equalTo(OK)

      contentAsString(bad) must contain ("login")
    }

    "redirect to the login page when user not authenticated" in new WithApplication {
      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)

      contentAsString(home) must contain ("login")
    }

    "render the index page when user is authenticated" in new WithApplication {
      val login = route(FakeRequest(GET, "/login")).get

      contentAsString(login) must contain ("CASA")

      val authRequest = FakeRequest().withSession().withFormUrlEncodedBody(userInput: _*)

      val result = Auth.authenticate(authRequest)

      redirectLocation(result) must beSome("/")
    }

  }
}
