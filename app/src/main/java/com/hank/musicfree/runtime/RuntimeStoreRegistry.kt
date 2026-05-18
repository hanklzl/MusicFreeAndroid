package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeStoreRegistry @Inject constructor(
    val stores: Set<@JvmSuppressWildcards RuntimeStore<*>>,
)
