package com.mohiva.play.silhouette.contrib

import com.mohiva.play.silhouette.core.{IdentityID, Identity, AuthenticationMethod}
import com.mohiva.play.silhouette.core.providers.{PasswordInfo, OAuth2Info, OAuth1Info}

/**
 * The default implementation of Identity.
 */
case class User(
  identityID: IdentityID,
  firstName: String,
  lastName: String,
  fullName: String,
  email: Option[String],
  avatarURL: Option[String],
  authMethod: AuthenticationMethod,
  oAuth1Info: Option[OAuth1Info] = None,
  oAuth2Info: Option[OAuth2Info] = None,
  passwordInfo: Option[PasswordInfo] = None) extends Identity
