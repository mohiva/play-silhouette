package com.mohiva.play.silhouette.impl.providers.cas

import org.jasig.cas.client.Protocol
import org.jasig.cas.client.authentication.AttributePrincipal
import org.jasig.cas.client.validation._
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Logger

object CASClient {
  val SERVICE_TICKET_PARAMETER = "ticket"
}

case class CasProtocol(protocol:Protocol,ticketValidatorFactory: CASSettings=>TicketValidator)

object CasProtocols {
  private def casValidatorWithEncoding[T <: AbstractUrlBasedTicketValidator](constructor: String => T) = {
    (settings: CASSettings) =>
      val result = constructor(settings.casURL)
      result.setEncoding(settings.encoding)
      result
  }
  private val protocols: Map[String,CasProtocol]=Map(
    "CAS10" -> new CasProtocol(Protocol.CAS1, casValidatorWithEncoding(new Cas10TicketValidator(_))),
    "CAS20" -> new CasProtocol(Protocol.CAS2, casValidatorWithEncoding(new Cas20ServiceTicketValidator(_))),
    "CAS30" -> new CasProtocol(Protocol.CAS3, casValidatorWithEncoding(new Cas30ServiceTicketValidator(_))),
    "SAML" -> new CasProtocol(Protocol.SAML11,{ settings =>
      val result = new Saml11TicketValidator(settings.casURL)
      result.setTolerance(settings.samlTimeTolerance)
      result
    })
  )
  def apply(protocolId: String)=protocols(protocolId)
  def isValid(protocolId: String)=protocols.contains(protocolId)
  val default="CAS30"
}

class CASClient(val settings: CASSettings) extends Logger {
  lazy val protocol=CasProtocols(settings.protocol)
  lazy val validator=protocol.ticketValidatorFactory(settings)

  def validateServiceTicket(ticket: String)(implicit ec: ExecutionContext) : Future[AttributePrincipal] = Future {
    validator.validate(ticket, settings.redirectURL).getPrincipal
  }

  //Based on org.jasig.cas.client.util.CommonUtils, which causes a compilation error when pulled in
  lazy val redirectUrl = {
    val svcParamName=protocol.protocol.getServiceParameterName
    val cbUrl=java.net.URLEncoder.encode(settings.redirectURL, settings.encoding)
    s"${settings.casURL}${if(settings.casURL.contains("?")) "&" else "?"}$svcParamName=$cbUrl"
  }

  /*def initialiseCasProtocol() : TicketValidator = settings.protocol match {
    //case CasProtocol.CAS20_PROXY => getCAS20ProxyValidator()
    //case CasProtocol.CAS30_PROXY => getCAS30ProxyValidator()
  }

  def getCAS20ProxyValidator() : Cas20ProxyTicketValidator = {
    val result = new Cas20ProxyTicketValidator(settings.casURL)
    result.setEncoding(settings.encoding)
    result
  }

  def getCAS30ProxyValidator() : Cas30ProxyTicketValidator = {
    val result = new Cas30ProxyTicketValidator(settings.casURL)
    result.setEncoding(settings.encoding)
    result
  } */

}
