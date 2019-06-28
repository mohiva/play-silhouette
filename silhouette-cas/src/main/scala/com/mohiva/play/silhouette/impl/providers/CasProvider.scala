/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.providers

import java.net.URL

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.api.{ AuthInfo, Logger, LoginInfo }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.CasProvider._
import org.jasig.cas.client.Protocol
import org.jasig.cas.client.authentication.AttributePrincipal
import org.jasig.cas.client.validation.{ AbstractUrlBasedTicketValidator, _ }
import play.api.mvc.{ Result, Results }

import com.mohiva.play.silhouette.ScalaCompat.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
 * The CAS authentication information.
 *
 * @param ticket The ticket.
 */
case class CasInfo(ticket: String) extends AuthInfo

/**
 * Base CAS provider.
 *
 * @see http://jasig.github.io/cas
 * @see https://github.com/Jasig/cas
 */
trait BaseCasProvider extends SocialProvider with CasProviderConstants with Logger {

  /**
   * The content type to parse a profile from.
   */
  override type Content = AttributePrincipal

  /**
   * The settings type.
   */
  override type Settings = CasSettings

  /**
   * The type of the auth info.
   */
  override type A = CasInfo

  /**
   * The provider ID.
   */
  override val id = ID

  /**
   * The CAS client instance.
   */
  val client: CasClient

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   *
   * Since profile retrieval is internal to the Jasig CAS  implementation, we don't need any further URLs ourselves.
   */
  override protected val urls: Map[String, String] = Map.empty

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, CasInfo]] = {
    Future.successful {
      request.extractString(CasClient.ServiceTicketParameter).map { ticket =>
        Right(CasInfo(ticket))
      }.getOrElse {
        Left(Results.Redirect(client.redirectURL))
      }
    }
  }

  /**
   * Populate the profile information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: CasInfo): Future[Profile] = {
    client.validateServiceTicket(authInfo.ticket).flatMap { principal =>
      profileParser.parse(principal, authInfo)
    }.transform(identity, { error =>
      new ProfileRetrievalException(SpecifiedProfileError.format(id, error.getMessage))
    })
  }
}

/**
 * The profile parser for the common social profile.
 */
class CasProfileParser
  extends SocialProfileParser[AttributePrincipal, CommonSocialProfile, CasInfo]
  with Logger
  with CasProviderConstants {

  /**
   * Parses the CAS profile.
   *
   * @param principal The principal returned from the provider.
   * @param authInfo  The auth info to query the provider again for additional data.
   * @return The CAS profile from given result.
   */
  def parse(principal: AttributePrincipal, authInfo: CasInfo) = Future.successful {
    val attr = principal.getAttributes

    logger.debug("AttributePrincipal, attributes:")
    attr.asScala.foreach { case (key, value) => logger.debug("key: [%s], value: [%s]".format(key, value)) }

    CommonSocialProfile(
      LoginInfo(ID, principal.getName),
      firstName = Option(attr.get(FirstName).asInstanceOf[String]),
      lastName = Option(attr.get(LastName).asInstanceOf[String]),
      email = Option(attr.get(Email).asInstanceOf[String]),
      avatarURL = Option(attr.get(PictureURL).asInstanceOf[String])
    )
  }
}

/**
 * The CAS provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param settings  The CAS provider settings.
 * @param client    The CAS client implementation.
 */
class CasProvider(
  protected val httpLayer: HTTPLayer,
  val settings: CasSettings,
  val client: CasClient)
  extends BaseCasProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = CasProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new CasProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = {
    new CasProvider(httpLayer, f(settings), client.withSettings(f))
  }
}

/**
 * The [[CasProvider]] companion object.
 */
object CasProvider extends CasProviderConstants {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s"
}

/**
 * The CAS provider constants.
 */
trait CasProviderConstants {
  val ID = "cas"
  val Email = "email"
  val FirstName = "first_name"
  val LastName = "family_name"
  val UserName = "username"
  val PictureURL = "picture_url"
}

/**
 * The CAS settings.
 *
 * @param casURL The URL of the CAS server.
 * @param redirectURL The URL the CAS server will redirect to.
 * @param encoding Specifies the encoding charset the client should use.
 * @param acceptAnyProxy Accept any proxy in a chain of proxies.
 * @param samlTimeTolerance Adjust to accommodate clock drift between client/server, increasing tolerance has security consequences.
 * @param protocol The protocol supported by the CAS server @see [[CasProtocol]].
 */
case class CasSettings(
  casURL: String,
  redirectURL: String,
  encoding: String = "UTF-8",
  acceptAnyProxy: Boolean = false,
  samlTimeTolerance: FiniteDuration = 1000 millis,
  protocol: CasProtocol.Value = CasProtocol.Default) {

  import CasSettings._

  /**
   * Validates the CAS settings.
   */
  if (isValidUrl(casURL))
    throw new ConfigurationException(CasUrlInvalid.format(ID, casURL))

  if (isValidUrl(redirectURL))
    throw new ConfigurationException(RedirectUrlInvalid.format(ID, redirectURL))

  if (encoding.isEmpty)
    throw new ConfigurationException(EncodingInvalid.format(ID))

  if (samlTimeTolerance.toMillis < 0)
    throw new ConfigurationException(TimeToleranceInvalid.format(ID, samlTimeTolerance))

  /**
   * Validates the given URL.
   *
   * @param url The URL to validate.
   * @return True if the URL is valid, false otherwise.
   */
  private def isValidUrl(url: String): Boolean = Try(new URL(url)).isFailure
}

/**
 * The [[CasSettings]] companion object.
 */
object CasSettings {

  /**
   * The CAS error messages
   */
  val CasUrlInvalid = "[Silhouette][%s] casURL setting [%s] is invalid"
  val RedirectUrlInvalid = "[Silhouette][%s] redirectURL setting [%s] is invalid"
  val EncodingInvalid = "[Silhouette][%s] encoding setting cannot be empty"
  val TimeToleranceInvalid = "[Silhouette][%s] samlTimeTolerance setting [%s] must be positive"
}

/**
 * The CAS client.
 *
 * @param settings The CAS settings.
 */
class CasClient(settings: CasSettings) extends Logger {

  /**
   * The CAS protocol.
   */
  lazy val protocol = CasProtocol(settings.protocol)

  /**
   * The CAS validator.
   */
  lazy val validator = protocol.ticketValidatorFactory(settings)

  /**
   * The redirect URL.
   *
   * Based on org.jasig.cas.client.util.CommonUtils, which causes a compilation error when pulled in.
   */
  lazy val redirectURL = {
    val svcParamName = protocol.protocol.getServiceParameterName
    val cbURL = java.net.URLEncoder.encode(settings.redirectURL, settings.encoding)
    s"${settings.casURL}${if (settings.casURL.contains("?")) "&" else "?"}$svcParamName=$cbURL"
  }

  /**
   * Validates the service ticket returned by the CAS server.
   *
   * @param ticket The ticket returned from the CAS server.
   * @param ec     The current ExecutionContext.
   * @return The attribute principal.
   */
  def validateServiceTicket(ticket: String)(implicit ec: ExecutionContext): Future[AttributePrincipal] = Future {
    validator.validate(ticket, settings.redirectURL).getPrincipal
  }

  /**
   * Gets a client initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the client initialized with new settings.
   */
  def withSettings(f: (CasSettings) => CasSettings): CasClient = new CasClient(f(settings))
}

/**
 * CasClient companion object.
 */
object CasClient {

  /**
   * Constants
   */
  val ServiceTicketParameter = "ticket"
}

/**
 * The CAS protocol.
 */
case class CasProtocol(protocol: Protocol, ticketValidatorFactory: CasSettings => TicketValidator)

/**
 * CasProtocol companion object.
 *
 * Helper to convert a protocol ID into a [[CasProtocol]] instance.
 *
 * Allowable values:
 *
 * "CAS10", "CAS20", "CAS30", "SAML"
 *
 * Default "CAS30"
 */
object CasProtocol extends Enumeration {

  val CAS10 = Value("CAS10")
  val CAS20 = Value("CAS20")
  val CAS30 = Value("CAS30")
  val SAML = Value("SAML")

  /**
   * The default cas protocol.
   */
  val Default = CAS30

  /**
   * Creates a protocol based on the protocol.
   *
   * @param protocol The protocol for which the protocol should be created.
   * @return The protocol instance for the given protocol ID.
   */
  def apply(protocol: CasProtocol.Value) = protocol match {
    case CasProtocol.CAS10 => new CasProtocol(Protocol.CAS1, casValidatorWithEncoding(new Cas10TicketValidator(_)))
    case CasProtocol.CAS20 => new CasProtocol(Protocol.CAS2, casValidatorWithEncoding(new Cas20ServiceTicketValidator(_)))
    case CasProtocol.CAS30 => new CasProtocol(Protocol.CAS3, casValidatorWithEncoding(new Cas30ServiceTicketValidator(_)))
    case CasProtocol.SAML => new CasProtocol(Protocol.SAML11, { settings =>
      val result = new Saml11TicketValidator(settings.casURL)
      result.setTolerance(settings.samlTimeTolerance.toMillis)
      result
    })
  }

  /**
   * A helper method which adds the encoding to the CAS validator.
   *
   * @param constructor The constructor of the validator.
   * @tparam T          The type of the CAS validator.
   * @return The CAS validator with the set encoding.
   */
  private def casValidatorWithEncoding[T <: AbstractUrlBasedTicketValidator](constructor: String => T) = {
    (settings: CasSettings) =>
      val result = constructor(settings.casURL)
      result.setEncoding(settings.encoding)
      result
  }
}
