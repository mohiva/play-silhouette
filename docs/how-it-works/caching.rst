Caching
=======

Silhouette caches some authentication artifacts. Here are some suggestions
on configuring or implementing caching in your application architecture
so that Silhouette can also use it.


Implement caching
-----------------

By default, Play is shipped with `EHCache`_, a
lightweight, in-memory cache. If this is not enough for your application
architecture, there are at least two other caching options to use with Silhouette.


Use another Play cache plugin
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Play has its own cache plugin architecture. So the easiest approach is to use
another cache plugin for Play. You can then use the `PlayCacheLayer`_
implementation and plug a new cache into your application.

.. _PlayCacheLayer: https://github.com/mohiva/play-silhouette/blob/master/silhouette/app/com/mohiva/play/silhouette/impl/util/PlayCacheLayer.scala


Implement your own cache layer
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Silhouette provides a `CacheLayer`_ trait which can be used to create a
custom cache implementation.

.. _CacheLayer: https://github.com/mohiva/play-silhouette/blob/master/silhouette/app/com/mohiva/play/silhouette/api/util/CacheLayer.scala


Clustered environment
---------------------

If you use a clustered environment for your application then make sure that
you use a distributed cache like `Redis`_ or `Memcached`_. Otherwise cached
artifacts must be synchronized between instances.


Development mode
----------------

When using the default Play cache in development mode, the cache will be
cleaned after every app reload. This is because Play's cache (`EHCache`_)
is configured to store the data only im memory by default.
This can be changed by overriding the shipped ``ehcache.xml`` from the
jars to persist the cache on the disk.

To change the default behaviour you must copy the ``ehcache.xml`` from the
distribution jars to your
configuration directory. Then add ``<diskStore path="java.io.tmpdir"/>`` and
change ``diskPersistent`` to ``true``. The following example shows a possible
configuration. Adapt it to fit your needs.

.. code-block:: xml

    <ehcache xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:noNamespaceSchemaLocation="../config/ehcache.xsd"
        updateCheck="false">

        <diskStore path="java.io.tmpdir"/>
        <defaultCache
            maxElementsInMemory="10000"
            eternal="false"
            timeToIdleSeconds="120"
            timeToLiveSeconds="120"
            overflowToDisk="false"
            maxElementsOnDisk="10000000"
            diskPersistent="true"
            diskExpiryThreadIntervalSeconds="120"
            memoryStoreEvictionPolicy="LRU"
        />
    </ehcache>

.. Note::
   You can also get around this issue by using a distributed cache like `Redis`_
   or `Memcached`_.

.. _EHCache: http://ehcache.org/
.. _Redis: http://redis.io/
.. _Memcached: http://memcached.org/
