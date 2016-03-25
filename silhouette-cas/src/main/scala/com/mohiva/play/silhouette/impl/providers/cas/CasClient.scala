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

import com.mohiva.play.silhouette.api.Logger
import org.jasig.cas.client.Protocol
import org.jasig.cas.client.authentication.AttributePrincipal
import org.jasig.cas.client.validation._

import scala.concurrent.{ ExecutionContext, Future }

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
