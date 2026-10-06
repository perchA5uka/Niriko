package com.otakup.niriko.navigation

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.viewmodel.CharacterDetailViewModel
import com.otakup.niriko.viewmodel.PersonDetailViewModel
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedEntryRestorationTest {
    private val remote = Proxy.newProxyInstance(SubjectRemoteDataSource::class.java.classLoader, arrayOf(SubjectRemoteDataSource::class.java)) { _, method, _ ->
        error("No network during first-frame seed assertion: " + method.name)
    } as SubjectRemoteDataSource

    @Test fun characterSharedTargetExistsBeforeNetworkWork() {
        AvatarNavigationSeed.remember("character_avatar_91001", "observed character", "image")
        val vm = CharacterDetailViewModel(remote, 91001L)
        val store = ViewModelStore(); store.put("character", vm)
        try {
            assertFalse(vm.uiState.value.isLoading)
            assertEquals("observed character", vm.uiState.value.detail!!.name)
            assertEquals("image", vm.uiState.value.detail!!.imageUrl)
            assertNull(vm.uiState.value.detail!!.summary)
            assertTrue(vm.uiState.value.subjects.isEmpty())
        } finally { store.clear() }
    }
    @Test fun personSharedTargetExistsBeforeNetworkWork() {
        AvatarNavigationSeed.remember("person_avatar_91002", "observed person", "image")
        val dao = Proxy.newProxyInstance(
            com.otakup.niriko.data.local.dao.PersonCollectionDao::class.java.classLoader,
            arrayOf(com.otakup.niriko.data.local.dao.PersonCollectionDao::class.java),
        ) { _, method, _ -> if (method.name == "exists") false else error("Unexpected person DAO call") }
            as com.otakup.niriko.data.local.dao.PersonCollectionDao
        val vm = PersonDetailViewModel(remote, dao, 91002L)
        val store = ViewModelStore(); store.put("person", vm)
        try {
            assertFalse(vm.uiState.value.isLoading)
            assertEquals("observed person", vm.uiState.value.detail!!.name)
            assertEquals("image", vm.uiState.value.detail!!.imageUrl)
            assertNull(vm.uiState.value.detail!!.birthday)
            assertTrue(vm.uiState.value.detail!!.career.isEmpty())
        } finally { store.clear() }
    }
    @Test fun unrelatedNavigationCannotReuseAnotherAvatarPreview() {
        assertNull(AvatarNavigationSeed.character(91999L))
        assertNull(AvatarNavigationSeed.person(91999L))
    }
    @Test fun childHeroIdentityIsInArgumentsBeforeCompositionNotWrittenAfterNavigate() {
        val nav = source("navigation/NirikoNavHost.kt")
        assertTrue(nav.contains("subject_detail/" + "$" + "id?heroKey="))
        assertTrue(nav.contains("android.net.Uri.encode(heroKey)"))
        assertTrue(nav.contains("""navArgument("heroKey")"""))
        assertTrue(nav.contains("""backStackEntry.arguments?.getString("heroKey")"""))
        assertFalse(nav.contains("""currentBackStackEntry?.savedStateHandle?.set("detail_hero_key"""))
        assertTrue(usesSubjectCoverTransition("subject_detail/{subjectId}?heroKey={heroKey}", "subject_detail/42?heroKey=source"))
        assertTrue(usesSubjectCoverTransition("main", "person_detail/42"))
    }
    @Test fun independentHorizontalRailStatesAreOwnedByParentEntryAndProtectEmptyMeasurements() {
        val rails = source("ui/subject/DetailRailState.kt")
        assertTrue(rails.contains("LocalDetailNavigationState.current"))
        assertTrue(rails.contains("state.layoutInfo.totalItemsCount > 0"))
        assertTrue(rails.contains("state.maxValue != Int.MAX_VALUE"))
        assertTrue(rails.contains("DisposableEffect(state, entry, key)"))
        assertTrue(source("ui/subject/RelationsSection.kt").contains("""rememberDetailLazyRailState("relations")"""))
        assertTrue(source("ui/subject/SubjectDetailScreen.kt").contains("""rememberDetailPixelRailState("recommendations")"""))
        val avatars = source("ui/subject/CharacterStaffSection.kt")
        assertTrue(avatars.contains("""rememberDetailLazyRailState("characters")"""))
        assertTrue(avatars.contains("""rememberDetailLazyRailState("staff")"""))
    }
    private fun source(file: String) = File("src/main/java/com/otakup/niriko/" + file).readText()
}
