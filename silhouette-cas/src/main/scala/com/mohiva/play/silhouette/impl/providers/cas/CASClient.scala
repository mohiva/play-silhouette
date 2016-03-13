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

import org.jasig.cas.client.Protocol
import org.jasig.cas.client.authentication.AttributePrincipal
import org.jasig.cas.client.validation._
import scala.concurrent.{ ExecutionContext, Future }
import com.mohiva.play.silhouette.api.Logger

/**
 * CASClient companion object 
 */
object CASClient {
  /**
   * Constants
   */
  val SERVICE_TICKET_PARAMETER = "ticket"
}

/**
 * The CASProtocol case class
 */
case class CASProtocol(protocol: Protocol, ticketValidatorFactory: CASSettings => TicketValidator)

/**
 * CasProtcols object converts string into a [[CasProtocol]] instance.
 * 
 * Allowable values:
 * 
 * "CAS10", "CAS20", "CAS30", "SAML"
 * 
 * Default "CAS30"
 */
object CasProtocols {
  
  val default = "CAS30"
  
  /**
   * Constructor
   */
  def apply(protocolId: String) = protocols(protocolId)

  /**
   * Validate protocol string
   */
  def isValid(protocolId: String) = protocols.contains(protocolId)
  
  private def protocols: Map[String, CASProtocol] = Map(
    "CAS10" -> new CASProtocol(Protocol.CAS1, casValidatorWithEncoding(new Cas10TicketValidator(_))),
    "CAS20" -> new CASProtocol(Protocol.CAS2, casValidatorWithEncoding(new Cas20ServiceTicketValidator(_))),
    "CAS30" -> new CASProtocol(Protocol.CAS3, casValidatorWithEncoding(new Cas30ServiceTicketValidator(_))),
    "SAML" -> new CASProtocol(Protocol.SAML11, { settings =>
      val result = new Saml11TicketValidator(settings.casURL)
      result.setTolerance(settings.samlTimeTolerance)
      result
    })
  )

  private def casValidatorWithEncoding[T <: AbstractUrlBasedTicketValidator](constructor: String => T) = {
    (settings: CASSettings) =>
      val result = constructor(settings.casURL)
      result.setEncoding(settings.encoding)
      result
  }  
}

/**
 * CASClient class
 */
class CASClient(val settings: CASSettings) extends Logger {
  lazy val protocol = CasProtocols(settings.protocol)
  lazy val validator = protocol.ticketValidatorFactory(settings)
  
  //Based on org.jasig.cas.client.util.CommonUtils, which causes a compilation error when pulled in
  lazy val redirectUrl = {
    val svcParamName = protocol.protocol.getServiceParameterName
    val cbUrl = java.net.URLEncoder.encode(settings.redirectURL, settings.encoding)
    s"${settings.casURL}${if (settings.casURL.contains("?")) "&" else "?"}$svcParamName=$cbUrl"
  }

  /**
   * Validates the service ticket returned by the CAS server.
   * 
   * @param the ticket returned from the CASserver
   * @param ec the current ExecutionContext
   */
  def validateServiceTicket(ticket: String)(implicit ec: ExecutionContext): Future[AttributePrincipal] = Future {
    validator.validate(ticket, settings.redirectURL).getPrincipal
  }
}