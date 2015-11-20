package com.mohiva.play.silhouette.impl.providers.cas

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.api.{Logger, LoginInfo}
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, SharedSpecification}
import org.jasig.cas.client.authentication.AttributePrincipal
import org.jasig.cas.client.validation.TicketValidationException
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution.Implicits._
import play.api.test.{FakeRequest, WithApplication}
import scala.concurrent.Future

/**
 * @author nshaw
 */
class CASProviderSpec extends SharedSpecification[CASAuthInfo] with Mockito with Logger with ThrownExpectations  {
  isolated

  val c = context

  "The `authenticate` method" should {

    implicit val req = FakeRequest(GET, "/")

    "redirect to CAS server if service ticket is not present in request" in new WithApplication {

      result(c.provider.authenticate()) {
        case result =>
        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome.which(_ == c.settings.redirectURL)
      }
    }

    "redirect to CAS server with the original requested URL if service ticket is not present in the request" in new WithApplication {

      implicit val req = FakeRequest(GET, c.redirectURLWithOrigin)

      result(c.provider.authenticate()) {
        case result =>
        status(result) must equalTo(SEE_OTHER)
        //redirectLocation(result) must beSome.which(_ == c.redirectURLWithOrigin)
      }
    }

    "return a valid CASAuthInfo object if service ticket is present in request" in new WithApplication {

      implicit val req = FakeRequest(GET, "/?ticket=%s".format(c.ticket))

      authInfo(c.provider.authenticate()) {
        case authInfo => authInfo must be equalTo CASAuthInfo(c.ticket)
      }
    }

    "validate settings and" in {

      "fail with a ConfigurationException if setting casURL is invalid" in new WithApplication {

        c.settings.casURL returns ""

        c.provider.authenticate() must throwA[ConfigurationException]
      }

/*      "fail with a ConfigurationException if setting callBackURL is invalid" in new WithApplication {

        c.settings.callBackURL returns ""

        c.provider.authenticate() must throwA[ConfigurationException]
      }*/


      "fail with a ConfigurationException if setting redirectURL is invalid" in new WithApplication {

        c.settings.redirectURL returns ""

        c.provider.authenticate() must throwA[ConfigurationException]//(message = "[Silhouette][cas] Redirect URL is undefined") //)

  //      failed[ConfigurationException](c.provider.authenticate()) {
  //        case e =>  e.getMessage must startWith(RedirectURLInvalid.format(c.provider.id))
  //      }
      }

      "fail with a ConfigurationException if setting samlTimeTolerance is negative" in new WithApplication {

        c.settings.samlTimeTolerance returns -1L

        c.provider.authenticate() must throwA[ConfigurationException]
      }

      "fail with a ConfigurationException if setting encoding is empty" in new WithApplication {

        c.settings.encoding returns ""

        c.provider.authenticate() must throwA[ConfigurationException]
      }
    }
  }

  "The `retrieveProfile` method" should {

    "return a valid profile if the CASClient validates the ticket" in new WithApplication {

      c.prinicpal.getAttributes returns c.attr

      c.client.validateServiceTicket(c.ticket) returns Future.successful(c.prinicpal)

      implicit val req = FakeRequest(GET, "/?ticket=%s".format(c.ticket))

      c.provider.authenticate()

      val futureProfile = for {
        a <- c.provider.authenticate()
        p <- c.provider.retrieveProfile(a.right.get)
      } yield p

      await(futureProfile) must beLike[CommonSocialProfile] {
        case profile => profile must be equalTo new CASProfile(new LoginInfo(CASProvider.ID, c.userName), Some(c.email),
                                                           Some(c.firstName), Some(c.lastName), Some(c.displayName),
                                                           Some(Gender.MALE), Some(new java.util.Locale(c.locale)),
                                                           Some(c.pictureURL), Some(c.profileURL), Some(c.location))
        }
    }

    "fail with a ProfileRetrievalException if service ticket can't be validated" in new WithApplication {

      await(c.client.validateServiceTicket(c.ticket)) must throwA[TicketValidationException]

      await(c.provider.retrieveProfile(c.authInfo)) must throwA[ProfileRetrievalException]
    }

//      "fail with an UnknownHostException if the casURL is invalid." in new WithApplication {
//
//        c.realProvider.retrieveProfile(c.authInfo) must throwA[UnknownHostException] //won't catch o_O
//
//      }
  }

  protected def context: CASProviderSpecContext = new CASProviderSpecContext {}

}

trait CASProviderSpecContext extends Scope with Mockito with ThrownExpectations {
  lazy val settings: CASSettings = spy(CASSettings(
      casURL = "https://cas-url/",
//      callBackURL = "https://localhost/cas/callback",
      redirectURL = "https://cas-redirect/"))

  lazy val httpLayer = {
    val m = mock[HTTPLayer]
    m.executionContext returns defaultContext
    m
  }

  lazy val redirectURLWithOrigin: String = "$s/my/original/url".format(settings.redirectURL)

  lazy val client: CASClient = mock[CASClient]

  lazy val realClient: CASClient = new CASClient(settings)

  lazy val provider: CASProvider = new CASProvider(httpLayer, settings, client)

  lazy val realProvider: CASProvider = new CASProvider(httpLayer, settings, realClient)

  lazy val ticket: String = "ST-12345678"

  lazy val authInfo: CASAuthInfo = new CASAuthInfo(ticket)

  lazy val prinicpal: AttributePrincipal = mock[AttributePrincipal]

  lazy val name = "abc123"
  lazy val email = "email"
  lazy val firstName = "Nick"
  lazy val lastName = "Shaw"
  lazy val displayName = "Delphian"
  lazy val userName = "314159"
  lazy val gender = "MALE"
  lazy val locale = "en-GB"
  lazy val pictureURL = "http://www.gravatar"
  lazy val profileURL = "http://www.g4me.co.uk/profile/314159"
  lazy val location = "LDN"

  lazy val attr = new java.util.HashMap[String, Object]();
      attr.put(CASProvider.Email, email)
      attr.put(CASProvider.FirstName, firstName)
      attr.put(CASProvider.LastName, lastName)
      attr.put(CASProvider.DisplayName, displayName)
      attr.put(CASProvider.UserName, userName)
      attr.put(CASProvider.Gender, gender)
      attr.put(CASProvider.Locale, locale)
      attr.put(CASProvider.PictureURL, pictureURL)
      attr.put(CASProvider.ProfileURL, profileURL)
      attr.put(CASProvider.Location, location)
}
