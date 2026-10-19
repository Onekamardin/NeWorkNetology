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
import ru.netology.nework.databinding.ScreenEventDetailBinding
import ru.netology.nework.domain.model.Event
import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.utils.DetailMapController
import ru.netology.nework.ui.utils.authorJobText
import ru.netology.nework.ui.utils.formatServerDate
import ru.netology.nework.ui.utils.loadServerImage
import ru.netology.nework.ui.utils.openLink
import ru.netology.nework.ui.utils.peopleNames
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.FeedActionResult
import ru.netology.nework.ui.viewmodel.EventViewModel

class EventDetailFragment : Fragment(R.layout.screen_event_detail) {
    private val viewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private val uiActions get() = activity as? AppUiActions
    private val editorViewModel: EventViewModel by activityViewModels()
    private var _binding: ScreenEventDetailBinding? = null
    private val binding get() = _binding!!
    private var eventId = 0L
    private var authorId: Long? = null
    private var mapController: DetailMapController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenEventDetailBinding.bind(view)
        authorId = null
        eventId = arguments?.getLong("event_id")?.takeIf { it > 0 }
            ?: viewModel.selectedEvent.value?.id ?: 0
        binding.detailRetry.setOnClickListener { viewModel.loadEvent(eventId) }
        if (viewModel.selectedEvent.value?.id != eventId) viewModel.loadEvent(eventId)
        binding.participationButton.setOnClickListener {
            val event = currentEvent() ?: return@setOnClickListener
            val user = authViewModel.currentUser()
            if (user == null) {
                uiActions?.showAuthRequired(getString(R.string.event_join_login))
            } else {
                viewModel.toggleEventParticipation(event, user.id)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.likeErrors.collect { uiActions?.showMessage(it) } }
                launch {
                    viewModel.messages.collect { result ->
                        if (findNavController().currentDestination?.id == R.id.eventDetailFragment) {
                            if (result == FeedActionResult.EventDeleted) {
                                uiActions?.showMessage(getString(R.string.event_deleted))
                                findNavController().navigateUp()
                            } else if (result is FeedActionResult.Error) uiActions?.showMessage(result.message)
                        }
                    }
                }
                launch {
                    viewModel.selectedEvent.collect { event ->
                        if (event?.id == eventId) {
                            if (authorId != event.authorId) {
                                authorId = event.authorId
                                viewModel.loadDetailJobs(event.authorId)
                                if ((event.speakerIds + event.participantsIds).any { it !in event.users } &&
                                    viewModel.users.value.items.isEmpty()) viewModel.loadUsers()
                            }
                            showEvent(event)
                        }
                    }
                }
                launch { viewModel.users.collect { currentEvent()?.let(::showPeople) } }
                launch { viewModel.detailJobs.collect { showAuthorJob() } }
                launch { viewModel.participationLoading.collect { currentEvent()?.let(::showParticipation) } }
                launch { authViewModel.auth.collect { currentEvent()?.let(::showParticipation) } }
                launch { viewModel.participationMessages.collect { uiActions?.showMessage(it) } }
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

    private fun currentEvent() = viewModel.selectedEvent.value?.takeIf { it.id == eventId }

    private fun showEvent(event: Event) {
        binding.detailAuthor.text = event.author ?: getString(R.string.user_fallback)
        binding.detailAvatar.loadServerImage(event.authorAvatar)
        binding.detailLikes.text = getString(
            if (event.likedByMe) R.string.likes_count_liked else R.string.likes_count, event.likeOwnerIds.size,
        )
        binding.detailLikes.setOnClickListener {
            if (authViewModel.isAuthenticated()) viewModel.toggleEventLike(event)
            else uiActions?.showAuthRequired(getString(R.string.auth_required_to_like))
        }
        binding.detailMenu.visibility = if (authViewModel.currentUser()?.id == event.authorId) View.VISIBLE else View.GONE
        binding.detailMenu.setOnClickListener { button ->
            PopupMenu(requireContext(), button).apply {
                menu.add(R.string.edit).setOnMenuItemClickListener {
                    editorViewModel.startEditing(event)
                    findNavController().navigate(R.id.action_global_createEventFragment)
                    true
                }
                menu.add(R.string.delete).setOnMenuItemClickListener {
                    uiActions?.confirmDeleteEvent(event)
                    true
                }
                show()
            }
        }
        val type = getString(if (event.type.equals("OFFLINE", true)) R.string.event_offline else R.string.event_online)
        binding.detailDate.text = getString(R.string.detail_event_dates, formatServerDate(event.published),
            getString(R.string.event_date_line, type, formatServerDate(event.datetime)))
        binding.detailContent.text = event.content
        val attachment = event.attachment
        val image = attachment?.takeIf { it.type.equals("IMAGE", true) || it.type.startsWith("image/", true) }
        binding.detailAttachmentImage.visibility = if (image == null) View.GONE else View.VISIBLE
        binding.detailAttachmentImage.loadServerImage(image?.url)
        val media = attachment?.takeIf { it != image }
        binding.detailAttachmentOpen.visibility = if (media == null) View.GONE else View.VISIBLE
        binding.detailAttachmentOpen.setText(
            if (media?.type.equals("VIDEO", true) || media?.type?.startsWith("video/", true) == true)
                R.string.attachment_open_video else R.string.attachment_open_audio,
        )
        binding.detailAttachmentOpen.setOnClickListener { media?.let { openLink(requireContext(), it.url) } }
        binding.detailLink.text = event.link
        binding.detailLink.visibility = if (event.link.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailLink.setOnClickListener { event.link?.let { openLink(requireContext(), it) } }
        showPeople(event)
        showAuthorJob()
        showParticipation(event)
        val coords = event.coords
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

    private fun showParticipation(event: Event) {
        val userId = authViewModel.currentUser()?.id
        val loading = viewModel.participationLoading.value == event.id
        binding.participationButton.isEnabled = !loading
        binding.participationButton.setText(when {
            loading -> R.string.event_join_loading
            userId == null -> R.string.event_join_login
            userId in event.participantsIds || event.participatedByMe -> R.string.event_leave
            else -> R.string.event_join
        })
    }

    private fun showPeople(event: Event) {
        val users = viewModel.users.value.items
        binding.detailParticipants.text = peopleNames(requireContext(), event.participantsIds, event.users, users)
        binding.detailSpeakers.text = peopleNames(requireContext(), event.speakerIds, event.users, users)
    }

    private fun showAuthorJob() {
        binding.detailAuthorJob.text = authorJobText(requireContext(), viewModel.detailJobs.value, currentEvent()?.authorJob)
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.detail_event_title))
        currentEvent()?.let(::showParticipation)
    }

    override fun onDestroyView() {
        mapController = null
        _binding = null
        super.onDestroyView()
    }
}

