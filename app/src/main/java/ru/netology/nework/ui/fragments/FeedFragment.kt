package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.PostViewModel
import ru.netology.nework.ui.viewmodel.EventViewModel
import ru.netology.nework.ui.viewmodel.FeedState
import ru.netology.nework.ui.viewmodel.FeedActionResult
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.domain.model.Event
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.databinding.ScreenFeedBinding
import ru.netology.nework.ui.adapter.EventAdapter
import ru.netology.nework.ui.adapter.PostAdapter
import ru.netology.nework.ui.adapter.UserAdapter

class FeedFragment : Fragment(R.layout.screen_feed) {
    private val viewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private val postViewModel: PostViewModel by activityViewModels()
    private val eventViewModel: EventViewModel by activityViewModels()
    private var _binding: ScreenFeedBinding? = null
    private val binding get() = _binding!!
    private var selectedTab = R.id.menu_posts
    private val uiActions get() = activity as? AppUiActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTab = savedInstanceState?.getInt(STATE_TAB) ?: R.id.menu_posts
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private val postAdapter = PostAdapter(::onPostLike, { post ->
        viewModel.selectPost(post)
        findNavController().navigate(R.id.action_global_postDetailFragment)
    }, { authViewModel.currentUser()?.id }, { post -> uiActions?.confirmDeletePost(post) }, { post ->
        postViewModel.startEditing(post)
        findNavController().navigate(R.id.action_global_createPostFragment)
    })
    private val eventAdapter = EventAdapter(::onEventLike, { event ->
        viewModel.selectEvent(event)
        findNavController().navigate(R.id.action_global_eventDetailFragment)
    }, { authViewModel.currentUser()?.id }, { event -> uiActions?.confirmDeleteEvent(event) }, { event ->
        eventViewModel.startEditing(event)
        findNavController().navigate(R.id.action_global_createEventFragment)
    })
    private val userAdapter = UserAdapter { user -> openUserProfile(user) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenFeedBinding.bind(view)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)
        selectTab(selectedTab)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.posts.collect { showPosts(it) } }
                launch { viewModel.events.collect { showEvents(it) } }
                launch { viewModel.users.collect { showUsers(it) } }
                launch {
                    viewModel.likeErrors.collect { uiActions?.showMessage(it) }
                }
                launch {
                    viewModel.messages.collect { result ->
                        if (findNavController().currentDestination?.id != R.id.feedFragment) return@collect
                        when (result) {
                            FeedActionResult.PostDeleted -> uiActions?.showMessage(getString(R.string.post_deleted))
                            FeedActionResult.EventDeleted -> uiActions?.showMessage(getString(R.string.event_deleted))
                            is FeedActionResult.Error -> uiActions?.showMessage(result.message)
                        }
                    }
                }
            }
        }
    }

    fun selectTab(tabId: Int) {
        selectedTab = tabId
        val title = when (tabId) {
            R.id.menu_events -> getString(R.string.events)
            R.id.menu_users -> getString(R.string.users)
            else -> getString(R.string.posts)
        }
        val showCreate = tabId == R.id.menu_posts || tabId == R.id.menu_events
        uiActions?.updateFeed(tabId, title, showCreate)

        binding.recyclerView.adapter = when (tabId) {
            R.id.menu_events -> eventAdapter
            R.id.menu_users -> userAdapter
            else -> postAdapter
        }
        when (tabId) {
            R.id.menu_events -> viewModel.loadEvents()
            R.id.menu_users -> viewModel.loadUsers()
            else -> viewModel.loadPosts()
        }
    }

    fun onCreateButtonPressed() {
        if (!authViewModel.isAuthenticated()) {
            uiActions?.showAuthRequired()
            return
        }

        if (selectedTab == R.id.menu_events) {
            eventViewModel.resetEventDraft()
            findNavController().navigate(R.id.action_global_createEventFragment)
        } else {
            postViewModel.clearEditing()
            postViewModel.clearCreatePostState()
            findNavController().navigate(R.id.action_global_createPostFragment)
        }
    }

    private fun openUserProfile(user: User) {
        findNavController().navigate(
            R.id.action_global_profileFragment,
            ProfileFragment.createArguments(user),
        )
    }

    private fun onPostLike(post: Post) {
        if (!authViewModel.isAuthenticated()) {
            uiActions?.showAuthRequired(getString(R.string.auth_required_to_like))
        } else {
            viewModel.togglePostLike(post)
        }
    }

    private fun onEventLike(event: Event) {
        if (!authViewModel.isAuthenticated()) {
            uiActions?.showAuthRequired(getString(R.string.auth_required_to_like))
        } else {
            viewModel.toggleEventLike(event)
        }
    }

    private fun showPosts(state: FeedState<Post>) {
        if (selectedTab != R.id.menu_posts) return
        showListState(state.loading, state.error, state.items.isEmpty(), R.string.no_posts)
        postAdapter.submitList(state.items)
    }

    private fun showEvents(state: FeedState<Event>) {
        if (selectedTab != R.id.menu_events) return
        showListState(state.loading, state.error, state.items.isEmpty(), R.string.no_events)
        eventAdapter.submitList(state.items)
    }

    private fun showUsers(state: FeedState<User>) {
        if (selectedTab != R.id.menu_users) return
        showListState(state.loading, state.error, state.items.isEmpty(), R.string.no_users)
        userAdapter.submitList(state.items)
    }

    private fun showListState(loading: Boolean, error: String?, empty: Boolean, emptyText: Int) {
        val screen = _binding ?: return
        screen.progressBar.visibility = if (loading && empty) View.VISIBLE else View.GONE
        val message = error ?: if (empty && !loading) getString(emptyText) else null
        screen.messageText.text = message
        screen.messageText.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        val title = when (selectedTab) {
            R.id.menu_events -> getString(R.string.events)
            R.id.menu_users -> getString(R.string.users)
            else -> getString(R.string.posts)
        }
        uiActions?.updateFeed(selectedTab, title, selectedTab != R.id.menu_users)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val STATE_TAB = "selected_feed_tab"
    }
}
