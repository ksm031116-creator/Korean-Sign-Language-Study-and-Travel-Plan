package app.signkorean.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 이동 규칙 (PRD §14: Back 은 한 단계, 루트 탭 전환은 각 탭의 스택 보존). */
class AppNavigatorTest {

    private val nav = AppNavigator()
    private fun current() = nav.state.value.current.route
    private fun routes() = nav.state.value.stack.map { it.route }

    @Test
    fun `처음에는 Explore 루트`() {
        assertEquals(RootTab.Explore, nav.state.value.tab)
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `학습을 열 때 스택에 학습 화면이 이미 있으면 그 위와 함께 닫고 새로 연다`() {
        nav.push(Route(ScreenId.SC03, "area:myeongdong"))
        nav.openLearn()
        val first = nav.state.value.current.id
        nav.push(Route(ScreenId.SC08, "t-market-1"))
        nav.push(Route(ScreenId.SC07, "gwangjang"))
        nav.openLearn() // SC07 의 Practise these signs
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC03, "area:myeongdong"), Route(ScreenId.SC04)), routes())
        assertNotEquals("새 항목이라 새 ViewModel 을 받는다", first, nav.state.value.current.id)
        assertTrue(nav.back())
        assertEquals(Route(ScreenId.SC03, "area:myeongdong"), current())
    }

    @Test
    fun `학습 화면이 없으면 그냥 위에 연다`() {
        nav.push(Route(ScreenId.SC03, "basics"))
        nav.openLearn()
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC03, "basics"), Route(ScreenId.SC04)), routes())
    }

    @Test
    fun `push 한 화면에서 Back 은 한 단계 돌아간다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.push(Route(ScreenId.SC04, "area:myeongdong"))
        assertTrue(nav.back())
        assertEquals(Route(ScreenId.SC03, "myeongdong"), current())
    }

    @Test
    fun `탭을 바꿨다 돌아오면 그 탭의 스택이 그대로다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.selectTab(RootTab.Saved)
        assertEquals(Route(ScreenId.SC09), current())
        nav.push(Route(ScreenId.SC08, "b01"))
        nav.selectTab(RootTab.Explore)
        assertEquals(Route(ScreenId.SC03, "myeongdong"), current())
        nav.selectTab(RootTab.Saved)
        assertEquals(Route(ScreenId.SC08, "b01"), current())
    }

    @Test
    fun `지금 탭을 다시 누르면 그 탭의 루트로 간다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.selectTab(RootTab.Explore)
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `다른 탭의 루트에서 Back 은 Explore 로, Explore 루트에서 Back 은 앱을 떠난다`() {
        nav.selectTab(RootTab.Plan)
        assertTrue(nav.back())
        assertEquals(RootTab.Explore, nav.state.value.tab)
        assertFalse(nav.back())
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `루트 화면을 push 하면 그 탭으로 간다`() {
        nav.push(Route(ScreenId.SC09))
        assertEquals(RootTab.Saved, nav.state.value.tab)
        assertEquals(1, nav.state.value.stack.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `루트 화면에는 인자를 줄 수 없다 (조용히 버리지 않는다)`() {
        nav.push(Route(ScreenId.SC09, "filter"))
    }

    @Test
    fun `같은 화면을 연달아 push 하면 한 번만 쌓인다 (빠른 두 번 탭)`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        assertEquals(2, nav.state.value.stack.size)
    }

    @Test
    fun `replaceTop 은 현재 화면만 바꾼다 (SC21 결제 성공 뒤 원래 동작으로)`() {
        nav.push(Route(ScreenId.SC21, "plan"))
        nav.replaceTop(Route(ScreenId.SC12))
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC12)), routes())
    }

    @Test
    fun `replaceTop 으로 루트 화면을 고르면 현재 화면을 닫고 그 탭으로 간다`() {
        nav.push(Route(ScreenId.SC21, "plan"))
        nav.replaceTop(Route(ScreenId.SC11))
        assertEquals(RootTab.Plan, nav.state.value.tab)
        assertEquals(listOf(Route(ScreenId.SC02)), nav.state.value.stacks.getValue(RootTab.Explore).map { it.route })
    }

    @Test
    fun `replaceTop 할 화면이 바로 아래에 있으면 겹치지 않고 그리로 돌아간다`() {
        nav.push(Route(ScreenId.SC12))
        nav.push(Route(ScreenId.SC21, "plan"))
        nav.replaceTop(Route(ScreenId.SC12))
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC12)), routes())
    }

    @Test
    fun `루트만 있을 때 replaceTop 은 루트를 지우지 않고 위에 쌓는다`() {
        nav.replaceTop(Route(ScreenId.SC03, "a"))
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC03, "a")), routes())
    }

    @Test
    fun `같은 화면을 다시 열면 새 항목이다 (지난번 입력·스크롤을 되살리지 않는다)`() {
        nav.push(Route(ScreenId.SC12))
        val first = nav.state.value.current.id
        nav.back()
        nav.push(Route(ScreenId.SC12))
        assertNotEquals(first, nav.state.value.current.id)
    }

    @Test
    fun `닫힌 항목을 알려 준다 (Back·탭 다시 누르기·replaceTop)`() {
        nav.push(Route(ScreenId.SC03, "a"))
        val a = nav.state.value.current.id
        nav.push(Route(ScreenId.SC04, "x"))
        val x = nav.state.value.current.id
        nav.back()
        assertEquals(setOf(x), nav.closedSince(setOf(a, x)))
        nav.selectTab(RootTab.Explore)
        assertEquals(setOf(a), nav.closedSince(setOf(a)))
    }

    @Test
    fun `모든 화면 ID 는 SC01~SC21 이고 SC05 는 없다 (v2_2 에서 삭제)`() {
        assertEquals(20, ScreenId.entries.size)
        assertFalse(ScreenId.entries.any { it.name == "SC05" })
        assertEquals(setOf(ScreenId.SC02, ScreenId.SC09, ScreenId.SC11), RootTab.entries.map { it.root }.toSet())
    }
}
