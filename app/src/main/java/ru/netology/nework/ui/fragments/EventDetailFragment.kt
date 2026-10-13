package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.AuthViewModel
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
import ru.netology.nework.domain.model.Event
import ru.netology.nework.databinding.ScreenEventDetailBinding

class EventDetailFragment : Fragment(R.layout.screen_event_detail) {
    private val viewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private val uiActions get() = activity as? AppUiActions
    private var _binding: ScreenEventDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenEventDetailBinding.bind(view)

        val event = viewModel.selectedEvent.value
        if (event == null) return

        showEvent(event)
        binding.participationButton.setOnClickListener {
            val user = authViewModel.currentUser()
            if (user == null) {
                uiActions?.showAuthRequired(getString(R.string.event_join_login))
            } else {
                viewModel.toggleEventParticipation(viewModel.selectedEvent.value ?: event, user.id)
            }
        }
        viewModel.loadDetailJobs(event.authorId)
        if ((event.speakerIds.isNotEmpty() || event.participantsIds.isNotEmpty()) &&
            viewModel.users.value.items.isEmpty()
        ) {
            viewModel.loadUsers()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.users.collect { showPeople(viewModel.selectedEvent.value ?: event) } }
                launch { viewModel.detailJobs.collect { showAuthorJob() } }
                launch {
                    viewModel.selectedEvent.collect { updated ->
                        if (updated?.id == event.id) {
                            binding.detailParticipants.text = peopleNames(updated.participantsIds)
                            showParticipation(updated)
                        }
                    }
                }
                launch {
                    viewModel.participationLoading.collect {
                        showParticipation(viewModel.selectedEvent.value ?: event)
                    }
                }
                launch {
                    viewModel.participationMessages.collect { uiActions?.showMessage(it) }
                }
            }
        }
    }

    private fun showEvent(event: Event) {
        binding.detailAuthor.text = event.author ?: getString(R.string.user_fallback)
        val eventType = if (event.type.equals("OFFLINE", ignoreCase = true)) {
            getString(R.string.event_offline)
        } else {
            getString(R.string.event_online)
        }
        binding.detailDate.text = getString(
            R.string.event_date_line,
            eventType,
            formatServerDate(event.datetime),
        )
        binding.detailContent.text = event.content
        val attachment = event.attachment
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
        binding.detailLink.text = event.link
        binding.detailLink.visibility = if (event.link.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailLink.setOnClickListener { event.link?.let { openLink(requireContext(), it) } }
        binding.detailParticipants.text = peopleNames(event.participantsIds)
        binding.detailSpeakers.text = peopleNames(event.speakerIds)
        showParticipation(event)
        showMapButton(binding.detailMapButton, event.coords)
    }

    private fun showParticipation(event: Event) {
        val button = _binding?.participationButton ?: return
        val userId = authViewModel.currentUser()?.id
        val isLoading = viewModel.participationLoading.value == event.id
        button.isEnabled = !isLoading
        button.text = when {
            isLoading -> getString(R.string.event_join_loading)
            userId == null -> getString(R.string.event_join_login)
            userId in event.participantsIds -> getString(R.string.event_leave)
            else -> getString(R.string.event_join)
        }
    }

    private fun showPeople(event: Event) {
        binding.detailParticipants.text = peopleNames(event.participantsIds)
        binding.detailSpeakers.text = peopleNames(event.speakerIds)
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
        (activity as? AppUiActions)?.setScreenTitle(getString(R.string.detail_event_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
