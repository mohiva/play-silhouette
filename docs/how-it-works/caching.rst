Caching
=======

Silhouette caches some authentication artifacts. So here you can find
some information on how you can configure or implement caching in a way
that fits into your application architecture.


Implement caching
-----------------

By default, Play gets shipped with `EHCache`_, which is configured as a
lightweight, in-memory cache. Is this not enough for your application
architecture, then there exists two possibilities to use another cache
with Silhouette.


Use another Play cache plugin
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Play has its own cache plugin architecture. So the easiest way is to use
another cache plugin for Play. You can then use the `PlayCacheLayer`_
implementation and plug in a new cache into your application.

.. _PlayCacheLayer: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/contrib/utils/PlayCacheLayer.scala


Implement own cache layer
^^^^^^^^^^^^^^^^^^^^^^^^^

Silhouette provides a `CacheLayer`_ trait which can be used to create a
custom cache implementation.

.. _CacheLayer: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/core/utils/CacheLayer.scala


Clustered environment
---------------------

If you use a clustered environment for your application then make sure that
you use a distributed cache like `Redis`_ or `Memcached`_. Otherwise cached
artifacts must be synchronized between instances.


Development mode
----------------

When using the default Play cache in development mode, then the cache gets
cleaned after every app reload. This is because, by default, the `EHCache`_
(the cache used by Play) is configured to store the data only im memory.
This can be changed by overriding the shipped ``ehcache.xml`` from the
jars, to persist the cache on the disk.

To change this default behaviour you must copy the ``ehcache.xml`` to your
configuration directory. Then add``<diskStore path="java.io.tmpdir"/>`` and
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
