package com.mohiva.play.silhouette.contrib

import com.mohiva.play.silhouette.core.{LoginInfo, Identity}

/**
 * The default implementation of Identity.
 */
case class User(
  loginInfo: LoginInfo,
  firstName: String,
  lastName: String,
  fullName: String,
  email: Option[String],
  avatarURL: Option[String]) extends Identity
