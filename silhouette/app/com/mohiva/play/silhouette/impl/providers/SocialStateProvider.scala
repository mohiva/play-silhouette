package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import play.api.mvc.Result

import scala.concurrent.Future

/**
 * A stateful result, wraps the Result with userState.
 *
 * @param result the result object, response header and body
 * @param userState the user state
 */
case class StatefulResult(result: Result, userState: Map[String, String])

trait SocialStateProvider extends SocialProvider {

  /**
   * Authenticates the user and returns the auth information.
   *
   * Returns either a AuthInfo if all went OK or a StatefulResult that the controller sends
   * to the browser (e.g.: in the case of OAuth where the user needs to be redirected to
   * the service provider).
   *
   * @param request The request.
   * @return Either a StatefulResult or the AuthInfo from the provider.
   */
  def authenticate[B](userState: Map[String, String])(implicit request: ExtractableRequest[B]): Future[Either[StatefulResult, A]]

}
