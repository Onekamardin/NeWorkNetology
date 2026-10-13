package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.utils.openLink
import ru.netology.nework.ui.utils.formatServerDate
import ru.netology.nework.ui.utils.loadServerImage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.ui.fragments.LocationPickerFragment.Companion.ARG_INITIAL_COORDINATES
import ru.netology.nework.ui.fragments.LocationPickerFragment.Companion.ARG_READ_ONLY
import ru.netology.nework.domain.model.Post
import ru.netology.nework.databinding.ScreenPostDetailBinding

class PostDetailFragment : Fragment(R.layout.screen_post_detail) {
    private val viewModel: FeedViewModel by activityViewModels()
    private var _binding: ScreenPostDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenPostDetailBinding.bind(view)

        val post = viewModel.selectedPost.value
        if (post == null) return

        showPost(post)
        viewModel.loadDetailJobs(post.authorId)
        if (post.mentionIds.isNotEmpty() && viewModel.users.value.items.isEmpty()) {
            viewModel.loadUsers()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.users.collect { showPeople(post) } }
                launch { viewModel.detailJobs.collect { showAuthorJob() } }
            }
        }
    }

    private fun showPost(post: Post) {
        binding.detailAuthor.text = post.author ?: getString(R.string.user_fallback)
        binding.detailDate.text = formatServerDate(post.published)
        binding.detailContent.text = post.content
        val attachment = post.attachment
        val image = attachment?.takeIf {
            it.type.equals("IMAGE", ignoreCase = true) || it.type.startsWith("image/", ignoreCase = true)
        }
        binding.detailAttachmentImage.visibility = if (image == null) View.GONE else View.VISIBLE
        binding.detailAttachmentImage.loadServerImage(image?.url)
        val mediaAttachment = attachment?.takeIf { it != image }
        binding.detailAttachmentOpen.visibility = if (mediaAttachment == null) View.GONE else View.VISIBLE
        binding.detailAttachmentOpen.text = mediaAttachment?.let {
            val labelId = if (it.type.equals("VIDEO", ignoreCase = true) || it.type.startsWith("video/", ignoreCase = true)) {
                R.string.attachment_open_video
            } else {
                R.string.attachment_open_audio
            }
            getString(labelId)
        }.orEmpty()
        binding.detailAttachmentOpen.setOnClickListener {
            mediaAttachment?.let { openLink(requireContext(), it.url) }
        }
        binding.detailLink.text = post.link
        binding.detailLink.visibility = if (post.link.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailLink.setOnClickListener { post.link?.let { openLink(requireContext(), it) } }
        binding.detailMentions.text = peopleNames(post.mentionIds)
        showMapButton(binding.detailMapButton, post.coords)
    }

    private fun showPeople(post: Post) {
        binding.detailMentions.text = peopleNames(post.mentionIds)
    }

    private fun showAuthorJob() {
        val state = viewModel.detailJobs.value
        binding.detailAuthorJob.text = when {
            state.loading -> getString(R.string.detail_job_loading)
            state.error != null -> getString(R.string.detail_job_load_error)
            else -> state.items.maxByOrNull { it.start }?.let { "${it.name} — ${it.position}" }
                ?: getString(R.string.detail_no_job)
        }
    }

    private fun peopleNames(ids: List<Long>): String {
        if (ids.isEmpty()) return getString(R.string.detail_no_people)
        val users = viewModel.users.value.items
        return ids.joinToString("\n") { id ->
            val user = users.firstOrNull { it.id == id }
            user?.name?.takeIf { it.isNotBlank() }
                ?: user?.login?.takeIf { it.isNotBlank() }?.let { "@$it" }
                ?: getString(R.string.detail_user_fallback, id)
        }
    }

    private fun showMapButton(button: com.google.android.material.button.MaterialButton, coords: Coordinates?) {
        if (coords == null) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.setOnClickListener {
            findNavController().navigate(
                R.id.action_global_locationPickerFragment,
                bundleOf(
                    ARG_INITIAL_COORDINATES to "${coords.latitude},${coords.longitude}",
                    ARG_READ_ONLY to true,
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppUiActions)?.setScreenTitle(getString(R.string.detail_post_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
