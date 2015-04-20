package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.oauth2.WeiboProvider._
import com.mohiva.play.silhouette.api.{Logger, LoginInfo}
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.{UnexpectedResponseException, ProfileRetrievalException}
import com.mohiva.play.silhouette.impl.providers._
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Created by MiserCoal on 4/19/15.
 */
abstract class  WeiboProvider (httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) with Logger {

  /**
   * The content type to parse a profile from.
   */
  type Content = JsValue

  /**
   * The provider ID.
   */
  val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  protected val urls = Map("api" -> API)

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.params.get.get("uid").get.stripPrefix("\"").stripSuffix("\""), authInfo.accessToken)).get().flatMap { response =>
    val json = response.json
      logger.debug("yuzhou " + json.toString())
      (json \ "error").asOpt[JsObject] match {
        case Some(errorMsg) =>
          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorMsg))
        case _ => profileParser.parse(json)
      }
    }
  }

  /**
   * Builds the OAuth2 info.
   *
   * Weibo should put uid to params map :-\
   *
   * @param response The response from the provider.
   * @return The OAuth2 info on success, otherwise an failure.
   */
  override protected def buildInfo(response: WSResponse): Try[OAuth2Info] = {
    response.json.validate[OAuth2Info].asEither.fold(
      error => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
      info => {
        val params = Some(Map[String, String]("uid" -> response.json.\("uid").toString().toString))
        Success(info.copy(params = params))
      }
    )
  }
}


class WeiboProfileParser extends  SocialProfileParser[JsValue, CommonSocialProfile] {
  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue) = Future.successful {
    val userID = (json \ "idstr").as[String]
    val fullName = (json \ "name").asOpt[String]
    val avatarURL = (json \ "avatar_hd").asOpt[String]
    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      fullName = fullName,
      avatarURL = avatarURL
    )
  }
}


/**
 * The profile builder for the common social profile.
 */
trait WeiboProfileBuilder extends CommonSocialProfileBuilder {
  self: WeiboProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new WeiboProfileParser
}

/**
 * The companion object.
 */
object WeiboProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Weibo constants.
   */
  val ID = "weibo"
  val API = "https://api.weibo.com/2/users/show.json?uid=%s&access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new WeiboProvider(httpLayer, stateProvider, settings) with WeiboProfileBuilder
  }
}
