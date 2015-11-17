package com.mohiva.play.silhouette.impl.providers.cas

import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.services.AuthInfo
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import scala.concurrent.Future
import play.api.mvc.Result
import play.core.j.JavaHelpers
import org.jasig.cas.client.authentication.AttributePrincipal
import play.api.mvc.Results
import play.libs.Scala
import scala.collection.JavaConverters
import com.mohiva.play.silhouette.api.Logger
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.cas.CASProvider._
import com.mohiva.play.silhouette.impl.providers.cas.CASProfileBuilder._
import java.util.Locale
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import java.net.URL
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.net.UnknownHostException

/**
 * @author nshaw
 */
class CASProvider(settings: CASSettings, client: CASClient) extends Provider with CASProfileBuilder with Logger {
  
  def id = ID
  
  //type Content = AttributePrincipal
  
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, CASAuthInfo]] = {
    validateSettings(settings)
    
    request.extractString(CASClient.SERVICE_TICKET_PARAMETER) match {
      case Some(ticket) => Future.successful(Right(CASAuthInfo(ticket)))
      case None => Future.successful(Left(Results.Redirect(settings.redirectURL)))
    }    
  }
  
  def retrieveProfile(authInfo: CASAuthInfo): Future[CASProfile] = {
    parse(authInfo, client)
  }
  
  private def validateSettings(settings: CASSettings) = {
    validateURL(settings.redirectURL) match { 
      case Success(s) =>   
      case Failure(e) => throw new ConfigurationException(RedirectURLInvalid.format(id, settings.redirectURL))}
    validateURL(settings.casURL) match { 
      case Success(s) =>
      case Failure(e) => throw new ConfigurationException(CASURLInvalid.format(id, settings.casURL))}
    validateURL(settings.callBackURL) match { 
      case Success(s) =>
      case Failure(e) => throw new ConfigurationException(CallBackURLInvalid.format(id, settings.callBackURL))}
    
    if (settings.samlTimeTolerance < 1L) throw new ConfigurationException(TimeToleranceInvalid.format(id, settings.samlTimeTolerance))
    if (settings.encoding.isEmpty()) throw new ConfigurationException(EncodingInvalid.format(id, settings.encoding))
  }
  
  private def validateURL(URL : String) : Try[URL] = Try (new URL(URL))

}

case class CASAuthInfo(ticket: String) extends AuthInfo

trait CASProfileBuilder {
  self: CASProvider =>
  
  def parse(authInfo: CASAuthInfo, client : CASClient) = Future.successful {
    
    client.validateServiceTicket(authInfo.ticket) match {
      case Some(principal) => {
        val attr = principal.getAttributes() 
        
        val locale = Option(attr.get(CASProvider.Locale).asInstanceOf[String]) match {
          case Some(s) => Some(new java.util.Locale(s))
          case None => None
        }
        
        val gender = Option(attr.get(CASProvider.Gender).asInstanceOf[String]) match {
          case Some(s) => Some(Gender.withName(s))
          case None => None
        }
        
        CASProfile(
          LoginInfo(ID, attr.get(CASProvider.UserName).asInstanceOf[String]),
          attr.get(CASProvider.Email).asInstanceOf[String],
          attr.get(CASProvider.FirstName).asInstanceOf[String],
          attr.get(CASProvider.LastName).asInstanceOf[String],
          Option(attr.get(CASProvider.DisplayName).asInstanceOf[String]),
          gender,
          locale,
          Option(attr.get(CASProvider.PictureURL).asInstanceOf[String]),
          Option(attr.get(CASProvider.ProfileURL).asInstanceOf[String]),
          Option(attr.get(CASProvider.Location).asInstanceOf[String])
        )  
      }
      case None => throw new ProfileRetrievalException(UnspecifiedProfileError.format(id))
    }
  }
}

object CASProfileBuilder {
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"
}

case class CASProfile (
  loginInfo: LoginInfo,
  email: String, 
  firstName: String, 
  lastName: String, 
  displayName: Option[String], 
  gender: Option[Gender.Value],
  locale: Option[Locale],
  pictureURL: Option[String],
  profileURL: Option[String],
  location: Option[String]
)

case class CASSettings(
  casURL: String,
  callBackURL: String,
  redirectURL: String,
  encoding: String = "UTF-8",
  acceptAnyProxy: Boolean = false,
  samlTimeTolerance: Long = 1000L,
  protocol: CasProtocol.Value = CasProtocol.CAS30
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
  val CallBackURLInvalid = "[Silhouette][%s] callBackURL setting [%s] is invalid"
  val TimeToleranceInvalid = "[Silhouette][%s] samlTimeTolerance setting [%s] must be positive"
  val EncodingInvalid = "[Silhouette][%s] encoding setting [%s] cannot be empty"
}