package com.mohiva.play.silhouette.impl.providers.state

import javax.inject.Inject

import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, IDGenerator }
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import com.mohiva.play.silhouette.impl.providers.{ PublishableSocialStateItemHandler, SocialStateItem, SocialStateItemHandler }
import play.api.libs.json.{ Format, Json }
import CsrfStateItemHandler._
import com.mohiva.play.silhouette.api.crypto.CookieSigner
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import play.api.mvc.{ Cookie, RequestHeader, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * Csrf State is a sub type of SocialStateItem
 *
 * @param value wrapper around the csrf state value
 */
case class CsrfState(value: String) extends SocialStateItem

/**
 * Handles csrf state.
 *
 * @param settings The state settings.
 * @param idGenerator The ID generator used to create the state value.
 * @param cookieSigner The cookie signer implementation.
 */
class CsrfStateItemHandler @Inject() (
  settings: CsrfStateSettings,
  idGenerator: IDGenerator,
  cookieSigner: CookieSigner) extends SocialStateItemHandler with PublishableSocialStateItemHandler {

  /**
   * The item the handler can handle.
   */
  override type Item = CsrfState

  /**
   * Gets the state item the handler can handle.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  override def item(implicit ec: ExecutionContext): Future[Item] = {
    idGenerator.generate.map { CsrfState(_) }
  }

  /**
   * Indicates if a handler can handle the given [[SocialStateItem]].
   *
   * This method should check if the [[serialize]] method of this handler can serialize the given
   * unserialized state item.
   *
   * @param item The item to check for.
   * @return [[Some]] casted state item if the handler can handle the given state item, [[None]] otherwise.
   */
  override def canHandle(item: SocialStateItem): Option[Item] = item match {
    case i: Item => Some(i)
    case _       => None
  }

  /**
   * Indicates if a handler can handle the given unserialized state item.
   *
   * This method should check if the [[unserialize]] method of this handler can unserialize the given
   * serialized state item.
   *
   * @param item The item to check for.
   * @param request The request instance to get additional data to validate against.
   * @return True if the handler can handle the given state item, false otherwise.
   */
  override def canHandle[B](item: ItemStructure)(implicit request: ExtractableRequest[B]): Boolean = {
    item.id == ID && {
      clientState match {
        case Success(token) => token == item.data.as[Item]
        case Failure(_)     => false
      }
    }
  }

  /**
   * Returns a serialized value of the state item.
   *
   * @param item The state item to serialize.
   * @return The serialized state item.
   */
  override def serialize(item: Item): ItemStructure = ItemStructure(ID, Json.toJson(item))

  /**
   * Unserializes the state item.
   *
   * @param item    The state item to unserialize.
   * @param request The request instance to get additional data to validate against.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The unserialized state item.
   */
  override def unserialize[B](item: ItemStructure)(implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Item] = {
    Future.fromTry(Try(item.data.as[Item]))
  }

  /**
   * Publishes the Csrf State to the client.
   *
   * @param item    The item to publish.
   * @param result  The result to send to the client.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](item: Item, result: Result)(implicit request: ExtractableRequest[B]): Result = {
    result.withCookies(Cookie(
      name = settings.cookieName,
      value = cookieSigner.sign(item.value),
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie))
  }

  /**
   * Gets the Csrf State from the cookie.
   *
   * @param request The request header.
   * @return The OAuth2 state on success, otherwise a failure.
   */
  private def clientState(implicit request: RequestHeader): Try[Item] = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => cookieSigner.extract(cookie.value).map(token => CsrfState(token))
      case None         => Failure(new OAuth2StateException(ClientStateDoesNotExists.format(settings.cookieName)))
    }
  }
}

/**
 * The companion object.
 */
object CsrfStateItemHandler {
  val ID = "csrf-state"

  /**
   * The error messages.
   */
  val ClientStateDoesNotExists = "[Silhouette][CookieState] State cookie doesn't exists for name: %s"
  val ProviderStateDoesNotExists = "[Silhouette][CookieState] Couldn't find state in request for param: %s"
  val StateIsNotEqual = "[Silhouette][CookieState] State isn't equal"
  val StateIsExpired = "[Silhouette][CookieState] State is expired"
  val InvalidJson = "[Silhouette][CookieState] Cannot parse invalid Json: %s"
  val InvalidStateFormat = "[Silhouette][CookieState] Cannot build OAuth2State because of invalid Json format: %s"
  val InvalidCookieSignature = "[Silhouette][CookieState] Invalid cookie signature"

  /**
   * Json Format for the Csrf State
   */
  implicit val csrfFormat: Format[CsrfState] = Json.format[CsrfState]
}

/**
 * The settings for the Csrf State.
 *
 * @param cookieName The cookie name.
 * @param cookiePath The cookie path.
 * @param cookieDomain The cookie domain.
 * @param secureCookie Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param expirationTime State expiration. Defaults to 5 minutes which provides sufficient time to log in, but
 *                       not too much. This is a balance between convenience and security.
 */
case class CsrfStateSettings(
  cookieName: String = "OAuth2CsrfState",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  expirationTime: FiniteDuration = 5 minutes)
