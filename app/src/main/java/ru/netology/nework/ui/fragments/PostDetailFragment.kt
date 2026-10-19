package ru.netology.nework.ui.fragments

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenPostDetailBinding
import ru.netology.nework.domain.model.Post
import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.utils.DetailMapController
import ru.netology.nework.ui.utils.authorJobText
import ru.netology.nework.ui.utils.formatServerDate
import ru.netology.nework.ui.utils.loadServerImage
import ru.netology.nework.ui.utils.openLink
import ru.netology.nework.ui.utils.peopleNames
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.FeedActionResult
import ru.netology.nework.ui.viewmodel.PostViewModel

class PostDetailFragment : Fragment(R.layout.screen_post_detail) {
    private val viewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private val uiActions get() = activity as? AppUiActions
    private val editorViewModel: PostViewModel by activityViewModels()
    private var _binding: ScreenPostDetailBinding? = null
    private val binding get() = _binding!!
    private var postId = 0L
    private var authorId: Long? = null
    private var mapController: DetailMapController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenPostDetailBinding.bind(view)
        authorId = null
        postId = arguments?.getLong("post_id")?.takeIf { it > 0 }
            ?: viewModel.selectedPost.value?.id ?: 0
        binding.detailRetry.setOnClickListener { viewModel.loadPost(postId) }
        if (viewModel.selectedPost.value?.id != postId) viewModel.loadPost(postId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.likeErrors.collect { uiActions?.showMessage(it) } }
                launch {
                    viewModel.messages.collect { result ->
                        if (findNavController().currentDestination?.id == R.id.postDetailFragment) {
                            if (result == FeedActionResult.PostDeleted) {
                                uiActions?.showMessage(getString(R.string.post_deleted))
                                findNavController().navigateUp()
                            } else if (result is FeedActionResult.Error) uiActions?.showMessage(result.message)
                        }
                    }
                }
                launch {
                    viewModel.selectedPost.collect { post ->
                        if (post?.id == postId) {
                            if (authorId != post.authorId) {
                                authorId = post.authorId
                                viewModel.loadDetailJobs(post.authorId)
                                if (post.mentionIds.any { it !in post.users } && viewModel.users.value.items.isEmpty()) {
                                    viewModel.loadUsers()
                                }
                            }
                            showPost(post)
                        }
                    }
                }
                launch { viewModel.users.collect { currentPost()?.let(::showPeople) } }
                launch { viewModel.detailJobs.collect { showAuthorJob() } }
                launch {
                    viewModel.detailLoading.collect {
                        binding.detailProgress.visibility = if (it) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.detailError.collect {
                        binding.detailError.text = it
                        binding.detailError.visibility = if (it == null) View.GONE else View.VISIBLE
                        binding.detailRetry.visibility = if (it == null) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun currentPost() = viewModel.selectedPost.value?.takeIf { it.id == postId }

    private fun showPost(post: Post) {
        binding.detailAuthor.text = post.author ?: getString(R.string.user_fallback)
        binding.detailAvatar.loadServerImage(post.authorAvatar)
        binding.detailLikes.text = getString(
            if (post.likedByMe) R.string.likes_count_liked else R.string.likes_count, post.likeOwnerIds.size,
        )
        binding.detailLikes.setOnClickListener {
            if (authViewModel.isAuthenticated()) viewModel.togglePostLike(post)
            else uiActions?.showAuthRequired(getString(R.string.auth_required_to_like))
        }
        binding.detailMenu.visibility = if (authViewModel.currentUser()?.id == post.authorId) View.VISIBLE else View.GONE
        binding.detailMenu.setOnClickListener { button ->
            PopupMenu(requireContext(), button).apply {
                menu.add(R.string.edit).setOnMenuItemClickListener {
                    editorViewModel.startEditing(post)
                    findNavController().navigate(R.id.action_global_createPostFragment)
                    true
                }
                menu.add(R.string.delete).setOnMenuItemClickListener {
                    uiActions?.confirmDeletePost(post)
                    true
                }
                show()
            }
        }
        binding.detailDate.text = formatServerDate(post.published)
        binding.detailContent.text = post.content
        val attachment = post.attachment
        val image = attachment?.takeIf {
            it.type.equals("IMAGE", true) || it.type.startsWith("image/", true)
        }
        binding.detailAttachmentImage.visibility = if (image == null) View.GONE else View.VISIBLE
        binding.detailAttachmentImage.loadServerImage(image?.url)
        val media = attachment?.takeIf { it != image }
        binding.detailAttachmentOpen.visibility = if (media == null) View.GONE else View.VISIBLE
        binding.detailAttachmentOpen.setText(
            if (media?.type.equals("VIDEO", true) || media?.type?.startsWith("video/", true) == true)
                R.string.attachment_open_video else R.string.attachment_open_audio,
        )
        binding.detailAttachmentOpen.setOnClickListener { media?.let { openLink(requireContext(), it.url) } }
        binding.detailLink.text = post.link
        binding.detailLink.visibility = if (post.link.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailLink.setOnClickListener { post.link?.let { openLink(requireContext(), it) } }
        showPeople(post)
        showAuthorJob()
        val coords = post.coords
        if (coords != null && mapController == null) {
            mapController = DetailMapController(binding.detailMap, viewLifecycleOwner)
        }
        mapController?.show(coords)
        binding.detailMapButton.visibility = if (coords == null) View.GONE else View.VISIBLE
        binding.detailMapButton.setOnClickListener {
            if (coords != null) findNavController().navigate(
                R.id.action_global_locationPickerFragment,
                bundleOf(
                    LocationPickerFragment.ARG_INITIAL_COORDINATES to "${coords.latitude},${coords.longitude}",
                    LocationPickerFragment.ARG_READ_ONLY to true,
                ),
            )
        }
    }

    private fun showPeople(post: Post) {
        binding.detailMentions.text = peopleNames(requireContext(), post.mentionIds, post.users, viewModel.users.value.items)
    }

    private fun showAuthorJob() {
        binding.detailAuthorJob.text = authorJobText(requireContext(), viewModel.detailJobs.value, currentPost()?.authorJob)
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppUiActions)?.setScreenTitle(getString(R.string.detail_post_title))
    }

    override fun onDestroyView() {
        mapController = null
        _binding = null
        super.onDestroyView()
    }
}

