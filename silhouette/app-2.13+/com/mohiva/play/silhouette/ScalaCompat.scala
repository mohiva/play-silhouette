package com.mohiva.play.silhouette

import scala.collection.immutable.Map

private[silhouette] object ScalaCompat {

  implicit class MapOps[K, V](val map: Map[K, V]) extends AnyVal {
    def transformValues[W](f: V => W): Map[K, W] = map.view.mapValues(f).toMap
  }

  val JavaConverters = scala.jdk.CollectionConverters
}
