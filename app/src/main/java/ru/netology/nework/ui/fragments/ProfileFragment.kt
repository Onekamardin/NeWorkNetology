package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.ProfileViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.ProfileState
import ru.netology.nework.ui.viewmodel.JobOperationState
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.FeedActionResult
import ru.netology.nework.ui.utils.loadServerImage

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
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.User
import ru.netology.nework.databinding.ScreenProfileBinding
import ru.netology.nework.ui.adapter.JobAdapter
import ru.netology.nework.ui.adapter.PostAdapter

class ProfileFragment : Fragment(R.layout.screen_profile) {


    private val viewModel: ProfileViewModel by activityViewModels()
    private val feedViewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenProfileBinding? = null
    private val binding get() = _binding!!
    private var selectedTab = 0
    private var profileUser: User? = null
    private val uiActions get() = activity as? AppUiActions
    private val postAdapter by lazy {
        PostAdapter({ post ->
            if (authViewModel.isAuthenticated()) viewModel.togglePostLike(post) else uiActions?.showAuthRequired(getString(R.string.auth_required_to_like))
        }, { post ->
            feedViewModel.selectPost(post)
            findNavController().navigate(R.id.action_global_postDetailFragment)
        }, { authViewModel.currentUser()?.id }, { post -> uiActions?.confirmDeletePost(post) })
    }
    private val jobAdapter by lazy { JobAdapter { uiActions?.confirmDeleteJob(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTab = requireArguments().getInt(ARG_TAB)
        profileUser = User(
            id = requireArguments().getLong(ARG_ID),
            login = requireArguments().getString(ARG_LOGIN),
            name = requireArguments().getString(ARG_NAME),
            avatar = requireArguments().getString(ARG_AVATAR),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenProfileBinding.bind(view)
        binding.profileRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.profileTabs.addTab(binding.profileTabs.newTab().setText(R.string.profile_wall))
        binding.profileTabs.addTab(binding.profileTabs.newTab().setText(R.string.profile_jobs))
        binding.profileTabs.getTabAt(selectedTab)?.select()
        binding.profileTabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                selectedTab = tab.position
                showProfile(viewModel.profile.value)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        binding.profileAddJob.setOnClickListener {
            if (!authViewModel.isAuthenticated()) {
                uiActions?.showAuthRequired()
            } else {
                viewModel.resetJobDraft()
                findNavController().navigate(R.id.action_global_createJobFragment)
            }
        }

        val user = profileUser ?: return
        val reload = requireArguments().getBoolean(ARG_RELOAD, true)
        if (reload) viewModel.loadProfile(user)
        else showProfile(viewModel.profile.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.profile.collect { showProfile(it) } }
                launch {
                    feedViewModel.messages.collect { result ->
                        when (result) {
                            FeedActionResult.PostDeleted -> {
                                uiActions?.showMessage(getString(R.string.post_deleted))
                                profileUser?.let(viewModel::loadProfile)
                            }
                            FeedActionResult.EventDeleted -> uiActions?.showMessage(getString(R.string.event_deleted))
                            is FeedActionResult.Error -> uiActions?.showMessage(result.message)
                        }
                    }
                }
                launch {
                    viewModel.jobOperation.collect { state ->
                        if (state is JobOperationState.Success) {
                            uiActions?.showMessage(getString(R.string.job_deleted))
                            viewModel.clearJobOperationState()
                            profileUser?.let(viewModel::loadProfile)
                        } else if (state is JobOperationState.Error) {
                            uiActions?.showMessage(state.message)
                            viewModel.clearJobOperationState()
                        }
                    }
                }
            }
        }
    }

    private fun showProfile(state: ProfileState) {
        val screen = _binding ?: return
        val user = state.user ?: profileUser ?: return
        screen.profileName.text = user.name?.takeIf { it.isNotBlank() } ?: getString(R.string.user_fallback)
        screen.profileLogin.text = user.login?.let { "@$it" }.orEmpty()
        val isMyProfile = user.id == authViewModel.currentUser()?.id
        screen.profileAddJob.visibility = if (isMyProfile && selectedTab == 1) View.VISIBLE else View.GONE
        screen.profileAvatar.loadServerImage(user.avatar)

        if (selectedTab == 0) {
            screen.profileRecycler.adapter = postAdapter
            postAdapter.submitList(state.wall.items)
            screen.profileProgress.visibility = if (state.wall.loading && state.wall.items.isEmpty()) View.VISIBLE else View.GONE
            val message = state.wall.error ?: if (state.wall.items.isEmpty() && !state.wall.loading) getString(R.string.profile_wall_empty) else null
            screen.profileMessage.text = message
            screen.profileMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        } else {
            screen.profileRecycler.adapter = jobAdapter
            jobAdapter.allowDelete = isMyProfile
            jobAdapter.submitList(state.jobs.items)
            screen.profileProgress.visibility = if (state.jobs.loading && state.jobs.items.isEmpty()) View.VISIBLE else View.GONE
            val message = state.jobs.error ?: if (state.jobs.items.isEmpty() && !state.jobs.loading) getString(R.string.profile_jobs_empty) else null
            screen.profileMessage.text = message
            screen.profileMessage.visibility = if (message == null) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        val user = profileUser
        uiActions?.setScreenTitle(user?.name ?: getString(R.string.user_fallback), user?.login?.let { "@$it" })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "user_id"
        private const val ARG_LOGIN = "user_login"
        private const val ARG_NAME = "user_name"
        private const val ARG_AVATAR = "user_avatar"
        private const val ARG_TAB = "profile_tab"
        private const val ARG_RELOAD = "reload_profile"

        fun createArguments(user: User, tab: Int = 0, reload: Boolean = true): Bundle = Bundle().apply {
            putLong(ARG_ID, user.id)
            putString(ARG_LOGIN, user.login)
            putString(ARG_NAME, user.name)
            putString(ARG_AVATAR, user.avatar)
            putInt(ARG_TAB, tab)
            putBoolean(ARG_RELOAD, reload)
        }
    }
}
