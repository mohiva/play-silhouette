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
import java.util.Locale

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.api.{ AuthInfo, Logger, LoginInfo }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.cas.CASProfileParser._
import com.mohiva.play.silhouette.impl.providers.cas.CASProvider._
import org.jasig.cas.client.authentication.AttributePrincipal
import play.api.mvc.{ Result, Results }
import scala.collection.JavaConversions._

import scala.concurrent.Future
import scala.util.Try

/**
 * Central Authentication Service Provider
 * Enterprise Single Sign-On
 *
 * @see http://jasig.github.io/cas
 * @see https://github.com/Jasig/cas
 */
class CASProvider(protected val httpLayer: HTTPLayer, val settings: CASSettings, client: CASClient)
  extends SocialProvider with CASProfileParser with CommonSocialProfileBuilder with Logger {

  type Content = AttributePrincipal

  override type Self = CASProvider
  override type A = CASAuthInfo
  override type Settings = CASSettings

  /**
   * The provider ID.
   */
  def id = ID

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, CASAuthInfo]] = {
    validateSettings(settings)

    Future.successful(
      request.extractString(CASClient.SERVICE_TICKET_PARAMETER).map(ticket =>
        Right(CASAuthInfo(ticket))).getOrElse(
        Left(Results.Redirect(client.redirectUrl)))
    )
  }

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = {
    val s = f(settings)
    new CASProvider(httpLayer, s, new CASClient(s))
  }

  /**
   * Returns the profile parser implementation.
   */
  override protected def profileParser: SocialProfileParser[AttributePrincipal, CommonSocialProfile, CASAuthInfo] = this

  /**
   * Populate the profile information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: CASAuthInfo): Future[CommonSocialProfile] =
    client.validateServiceTicket(authInfo.ticket).flatMap(principal =>
      parse(principal, authInfo)).transform(identity, { error =>
      new ProfileRetrievalException(UnspecifiedProfileError.format(id, error.getMessage))
    })

  /**
   * Gets the URLs that are needed to retrieve the profile data.
   *
   * Since profile retrieval is internal to the jasigcas implementation, we don't need any further URLs ourselves.
   */
  override protected def urls: Map[String, String] = Map.empty

  private def validateSettings(settings: CASSettings) = {
    if (validateURL(settings.redirectURL).isFailure)
      throw new ConfigurationException(RedirectURLInvalid.format(id, settings.redirectURL))

    if (validateURL(settings.casURL).isFailure)
      throw new ConfigurationException(CASURLInvalid.format(id, settings.casURL))

    if (!CasProtocols.isValid(settings.protocol))
      throw new ConfigurationException(ProtocolInvalid.format(id, settings.protocol))

    if (settings.samlTimeTolerance < 1L)
      throw new ConfigurationException(TimeToleranceInvalid.format(id, settings.samlTimeTolerance))

    if (settings.encoding.isEmpty())
      throw new ConfigurationException(EncodingInvalid.format(id, settings.encoding))
  }

  private def validateURL(URL: String): Try[URL] = Try(new URL(URL))
}

/**
 * The CAS authentication information
 */
case class CASAuthInfo(ticket: String) extends AuthInfo

/**
 * The profile parser for the [[CommonSocialProfile]]
 */
trait CASProfileParser extends SocialProfileParser[AttributePrincipal, CommonSocialProfile, CASAuthInfo] with Logger {
  def parse(principal: AttributePrincipal, authInfo: CASAuthInfo) = {

    val attr = principal.getAttributes

    logger.debug("AttributePrincipal, attributes:")
    attr.foreach(kv => logger.debug("key: [$s], value: [$s]".format(kv._1, kv._2)))

    val fName = Option(attr.get(CASProvider.FirstName).asInstanceOf[String])
    val sName = Option(attr.get(CASProvider.LastName).asInstanceOf[String])

    Future.successful(new CommonSocialProfile(
      LoginInfo(CASProvider.ID, attr.get(CASProvider.UserName).asInstanceOf[String]),
      firstName = fName,
      lastName = sName,
      email = Option(attr.get(CASProvider.Email).asInstanceOf[String]),
      avatarURL = Option(attr.get(CASProvider.PictureURL).asInstanceOf[String])
    ))
  }
}

/**
 * The CASProfile parser companion object.
 */
object CASProfileParser {
  /**
   * Error messages
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information: [%s]"
}

/**
 * The CAS settings
 *
 * @param casURL The URL of the CAS server.
 * @param redirectURL The URL the CAS server will redirect to.
 * @param encoding Specifies the encoding charset the client should use.
 * @param acceptAnyProxy Accept any proxy in a chain of proxies.
 * @param samlTimeTolerance Adjust to accommodate clock drift between client/server, increasing tolerance has security consequences
 * @param protocol The protocol supported by the CAS server @see CasProtocols
 *
 */
case class CASSettings(
  casURL: String,
  redirectURL: String,
  encoding: String = "UTF-8",
  acceptAnyProxy: Boolean = false,
  samlTimeTolerance: Long = 1000L,
  protocol: String = CasProtocols.default)

/**
 * The OAuth2Provider companion object.
 */
object CASProvider extends CASProviderConstants {

  /**
   * The CAS error messages
   */
  val RedirectURLInvalid = "[Silhouette][%s] redirectURL setting [%s] is invalid"
  val CASURLInvalid = "[Silhouette][%s] casUrl setting [%s] is invalid"
  val TimeToleranceInvalid = "[Silhouette][%s] samlTimeTolerance setting [%s] must be positive"
  val EncodingInvalid = "[Silhouette][%s] encoding setting [%s] cannot be empty"
  val ProtocolInvalid = "[Silhouette][%s] protocol setting [%s] is invalid"
}

/**
 * The CAS constants
 */
trait CASProviderConstants {
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
}