package com.otakup.niriko.navigation

import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.PersonDetailInfo

/** Small observed source metadata only, not a persistent database or downloaded bitmap cache. */
object AvatarNavigationSeed {
    data class Avatar(val name: String, val imageUrl: String?)
    private val entries = LinkedHashMap<String, Avatar>()
    @Synchronized fun remember(key: String, name: String, imageUrl: String?) {
        if (name.isBlank()) return
        entries.remove(key)
        entries[key] = Avatar(name, imageUrl)
        while (entries.size > 64) entries.remove(entries.keys.first())
    }
    @Synchronized fun character(id: Long): CharacterDetailInfo? = entries["character_avatar_" + id]?.let {
        CharacterDetailInfo(id, it.name, null, null, it.imageUrl, null)
    }
    @Synchronized fun person(id: Long): PersonDetailInfo? = entries["person_avatar_" + id]?.let {
        PersonDetailInfo(id, it.name, null, null, it.imageUrl, emptyList())
    }
}
