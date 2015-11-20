package com.mohiva.play.silhouette.impl.providers.cas

import java.net.URL
import java.util.Locale

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.util.{ExtractableRequest, HTTPLayer}
import com.mohiva.play.silhouette.api.{AuthInfo, Logger, LoginInfo}
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.cas.CASProfileBuilder._
import com.mohiva.play.silhouette.impl.providers.cas.CASProvider._
import org.jasig.cas.client.authentication.AttributePrincipal
import play.api.mvc.{Result, Results}

import scala.concurrent.Future
import scala.util.Try

/**
 * @author nshaw
 */
class CASProvider(protected val httpLayer: HTTPLayer,val settings: CASSettings, client: CASClient)
    extends SocialProvider with CASProfileBuilder with CommonSocialProfileBuilder with Logger {

  def id = ID

  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, CASAuthInfo]] = {
    validateSettings(settings)

    Future.successful(
      request.extractString(CASClient.SERVICE_TICKET_PARAMETER).map(ticket => Right(CASAuthInfo(ticket)))
      .getOrElse(Left(Results.Redirect(client.redirectUrl)))
    )
  }

  private def validateSettings(settings: CASSettings) = {
    if (validateURL(settings.redirectURL).isFailure)
      throw new ConfigurationException(RedirectURLInvalid.format(id, settings.redirectURL))
    if (validateURL(settings.casURL).isFailure)
      throw new ConfigurationException(CASURLInvalid.format(id, settings.casURL))
    if (!CasProtocols.isValid(settings.protocol)) throw new ConfigurationException(ProtocolInvalid.format(id,settings.protocol))
    if (settings.samlTimeTolerance < 1L) throw new ConfigurationException(TimeToleranceInvalid.format(id, settings.samlTimeTolerance))
    if (settings.encoding.isEmpty()) throw new ConfigurationException(EncodingInvalid.format(id, settings.encoding))
  }

  private def validateURL(URL : String) : Try[URL] = Try (new URL(URL))

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = {
    val s=f(settings)
    new CASProvider(httpLayer,s,new CASClient(s))
  }

  override type Self = CASProvider
  override type A = CASAuthInfo
  override type Settings = CASSettings
  type Content = AttributePrincipal

  /**
   * Returns the profile parser implementation.
   */
  override protected def profileParser: SocialProfileParser[AttributePrincipal, CommonSocialProfile] = this

  /**
   * Populate the profile information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: CASAuthInfo): Future[CommonSocialProfile] =
    client.validateServiceTicket(authInfo.ticket).flatMap(parse).transform(identity,{ error =>
      new ProfileRetrievalException(UnspecifiedProfileError.format(id))
    })

  /**
   * Gets the URLs that are needed to retrieve the profile data.
   *
   * Since profile retrieval is internal to the jasigcas implementation, we don't need any further URLs ourselves.
   */
  override protected def urls: Map[String, String] = Map.empty
}

case class CASAuthInfo(ticket: String) extends AuthInfo

trait CASProfileBuilder extends SocialProfileParser[AttributePrincipal,CommonSocialProfile] {
  def parse(principal: AttributePrincipal) = {
    val attr = principal.getAttributes()

    val locale = Option(attr.get(CASProvider.Locale).asInstanceOf[String]).map(new java.util.Locale(_))
    val gender = Option(attr.get(CASProvider.Gender).asInstanceOf[String]).map(Gender.withName)

    Future.successful(new CASProfile(
      LoginInfo(CASProvider.ID, principal.getName),
      Option(attr.get(CASProvider.Email).asInstanceOf[String]),
      Option(attr.get(CASProvider.FirstName).asInstanceOf[String]),
      Option(attr.get(CASProvider.LastName).asInstanceOf[String]),
      Option(attr.get(CASProvider.DisplayName).asInstanceOf[String]),
      gender,
      locale,
      Option(attr.get(CASProvider.PictureURL).asInstanceOf[String]),
      Option(attr.get(CASProvider.ProfileURL).asInstanceOf[String]),
      Option(attr.get(CASProvider.Location).asInstanceOf[String])
    ))
  }
}

object CASProfileBuilder {
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"
}

class CASProfile (
  override val loginInfo: LoginInfo,
  override val email: Option[String],
  override val firstName: Option[String],
  override val lastName: Option[String],
  val displayName: Option[String],
  val gender: Option[Gender.Value],
  val locale: Option[Locale],
  val pictureURL: Option[String],
  val profileURL: Option[String],
  val location: Option[String]
) extends CommonSocialProfile(loginInfo,firstName,lastName,displayName,email,pictureURL)

case class CASSettings (
  casURL: String,
  redirectURL: String,
  encoding: String = "UTF-8",
  acceptAnyProxy: Boolean = false,
  samlTimeTolerance: Long = 1000L,
  protocol: String = CasProtocols.default
)

object Gender extends Enumeration {
  type Gender = Value

  val MALE = Value("MALE")
  val FEMALE = Value("FEMALE")
  val UNSPECIFIED = Value("UNSPECIFIED")
}

object CASProvider {
  val ID = "cas"
  val Email = "email"
  val FirstName = "first_name"
  val LastName = "family_name"
  val DisplayName = "display_name"
  val UserName = "username"
  val Gender = "gender"
  val Locale = "locale"
  val PictureURL = "picture_url"
  val ProfileURL = "profile_url"
  val Location = "location"

  // Exception messages
  val RedirectURLInvalid = "[Silhouette][%s] redirectURL setting [%s] is invalid"
  val CASURLInvalid = "[Silhouette][%s] casUrl setting [%s] is invalid"
  val TimeToleranceInvalid = "[Silhouette][%s] samlTimeTolerance setting [%s] must be positive"
  val EncodingInvalid = "[Silhouette][%s] encoding setting [%s] cannot be empty"
  val ProtocolInvalid = "[Silhouette][%s] protocol setting [%s] is invalid"
}
