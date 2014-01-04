package com.mohiva.play.silhouette.core

/**
 * A trait to define Authorization objects that let you hook
 * an authorization implementation in SecuredActions.
 */
trait Authorization {

  /**
   * Checks whether the user is authorized to execute an action or not.
   *
   * @param identity The identity to check for.
   * @tparam I The type of the identity.
   * @return True if the user is authorized, false otherwise.
   */
  def isAuthorized[I <: Identity](identity: I): Boolean
}
