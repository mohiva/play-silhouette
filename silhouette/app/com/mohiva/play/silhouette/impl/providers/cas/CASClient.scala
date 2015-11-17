package com.mohiva.play.silhouette.impl.providers.cas

import org.jasig.cas.client.validation.TicketValidator
import org.jasig.cas.client.validation.Cas10TicketValidator
import org.jasig.cas.client.validation.Cas20ProxyTicketValidator
import org.jasig.cas.client.validation.Cas30ProxyTicketValidator
import org.jasig.cas.client.validation.Saml11TicketValidator
import play.Play
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator
import org.jasig.cas.client.validation.Cas30ServiceTicketValidator
import org.jasig.cas.client.validation.ProxyList
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import org.jasig.cas.client.validation.Assertion
import org.jasig.cas.client.authentication.AttributePrincipal
import scala.util.Try

object CASClient {
  val SERVICE_TICKET_PARAMETER = "ticket"
}

object CasProtocol extends Enumeration {
  type CasProtocol = Value
  
  val CAS10 = Value("CAS10")
  val CAS20 = Value("CAS20")
  val CAS30 = Value("CAS30")  
  
  val SAML = Value("SAML")  
}

class CASClient(val s: CASSettings) {
  
    def settings: CASSettings = s;
  
//    def getServiceTikcet[B](implicit request : ExtractableRequest[B], protocol : CasProtocol.Value) : Option[AttributePrincipal] = { 
//      val validator = initialiseCasProtocol(protocol)      
//      
//      request.extractString(CasClient.SERVICE_TICKET_PARAMETER) match { 
//        case Some(ticket) => val assertion : Assertion = validator.validate(ticket, settings.callBackURL)
//                             Some(assertion.getPrincipal())
//        case None => None
//      }
//    }
  
    def validateServiceTicket(ticket: String) : Option[AttributePrincipal] = {
      val validator = initialiseCasProtocol()
      
      validator.validate(ticket, settings.callBackURL) match {
         case assertion : Assertion => {
           assertion.isValid() match {
             case true => Some(assertion.getPrincipal)
             case false => None
           }
         }
       } 
    }
    
    def initialiseCasProtocol() : TicketValidator = settings.protocol match {
      case CasProtocol.CAS10 => getCAS10Validator()
      case CasProtocol.CAS20 => getCAS20Validator()
      case CasProtocol.CAS30 => getCAS30Validator()
      //case CasProtocol.CAS20_PROXY => getCAS20ProxyValidator()
      //case CasProtocol.CAS30_PROXY => getCAS30ProxyValidator()
      case CasProtocol.SAML => getSAMLValidator()
    }
    
    def getCAS10Validator() : Cas10TicketValidator = {
      val result = new Cas10TicketValidator(settings.casURL)
      result.setEncoding(settings.encoding)
      result
    }
    
    def getCAS20Validator() : Cas20ServiceTicketValidator = {
      val result = new Cas20ServiceTicketValidator(settings.casURL)
      result.setEncoding(settings.encoding)
      result
    }
    
    def getCAS30Validator() : Cas30ServiceTicketValidator = {
      val result = new Cas30ServiceTicketValidator(settings.casURL)
      result.setEncoding(settings.encoding)
      result
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
    }
    
    def getSAMLValidator() : Saml11TicketValidator = {
      val result = new Saml11TicketValidator(settings.casURL)
      result.setTolerance(settings.samlTimeTolerance)
      result
    }
}