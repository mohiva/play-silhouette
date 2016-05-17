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
package com.mohiva.play.silhouette.impl.providers.cas

import java.net.URL

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.api.{ AuthInfo, Logger, LoginInfo }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.cas.CasProvider._
import org.jasig.cas.client.authentication.AttributePrincipal
import play.api.mvc.{ Result, Results }

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
 * The CAS authentication information.
 *
 * @param ticket The ticket.
 */
case class CasAuthInfo(ticket: String) extends AuthInfo

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
  override type A = CasAuthInfo

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
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, CasAuthInfo]] = {
    Future.successful {
      request.extractString(CasClient.ServiceTicketParameter).map { ticket =>
        Right(CasAuthInfo(ticket))
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
  override protected def buildProfile(authInfo: CasAuthInfo): Future[Profile] = {
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
  extends SocialProfileParser[AttributePrincipal, CommonSocialProfile, CasAuthInfo]
  with Logger
  with CasProviderConstants {

  /**
   * Parses the CAS profile.
   *
   * @param principal The principal returned from the provider.
   * @param authInfo  The auth info to query the provider again for additional data.
   * @return The CAS profile from given result.
   */
  def parse(principal: AttributePrincipal, authInfo: CasAuthInfo) = Future.successful {
    val attr = principal.getAttributes

    logger.debug("AttributePrincipal, attributes:")
    attr.foreach { case (key, value) => logger.debug("key: [%s], value: [%s]".format(key, value)) }

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
