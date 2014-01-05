package com.mohiva.play.silhouette.core.utils

import scala.reflect.ClassTag
import scala.concurrent.Future
import play.api.cache.Cache
import play.api.Play.current

/**
 * Implementation of the cache layer which uses the default Play cache plugin.
 */
class PlayCacheLayer extends CacheLayer {

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time in seconds (0 second means eternity).
   * @tparam T The type of the object to store in cache.
   */
  def set[T](key: String, value: T, expiration: Int = 0): Future[Option[T]] = {
    Cache.set(key, value, expiration)
    Future.successful(Some(value))
  }

  /**
   * Retrieve a value from the cache.
   *
   * @param key Item key.
   * @tparam T The type of the object to return from cache.
   */
  def get[T](key: String)(implicit classTag: ClassTag[T]): Future[Option[T]] = Future.successful(Cache.getAs[T](key))

  /**
   * Remove a value from the cache.
   */
  def remove(key: String) = Cache.remove(key)
}
