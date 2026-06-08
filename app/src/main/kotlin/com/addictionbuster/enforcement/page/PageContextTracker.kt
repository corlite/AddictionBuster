package com.addictionbuster.enforcement.page

import com.addictionbuster.enforcement.AppIdentity
import com.addictionbuster.enforcement.PageSnapshot

class PageContextTracker {
    private val missingSinceByIdentity = mutableMapOf<String, Long>()

    @Synchronized
    fun update(
        identity: AppIdentity,
        pageSnapshot: PageSnapshot?,
        nowMillis: Long
    ): PageContextState {
        if (pageSnapshot != null) {
            missingSinceByIdentity.remove(identity.identityKey)
            return PageContextState(
                pageSnapshot = pageSnapshot,
                missingSinceMillis = null
            )
        }
        val missingSince = missingSinceByIdentity.getOrPut(identity.identityKey) { nowMillis }
        return PageContextState(
            pageSnapshot = null,
            missingSinceMillis = missingSince
        )
    }
}

data class PageContextState(
    val pageSnapshot: PageSnapshot?,
    val missingSinceMillis: Long?
)
