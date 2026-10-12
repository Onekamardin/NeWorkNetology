package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.AppUiActions
import ru.netology.nework.ui.FeedViewModel
import ru.netology.nework.ui.openLink

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.domain.model.Event
import ru.netology.nework.databinding.ScreenEventDetailBinding
import ru.netology.nework.ui.formatServerDate

class EventDetailFragment : Fragment(R.layout.screen_event_detail) {
    private val viewModel: FeedViewModel by activityViewModels()
    private var _binding: ScreenEventDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenEventDetailBinding.bind(view)

        val event = viewModel.selectedEvent.value
        if (event == null) return

        showEvent(event)
        viewModel.loadDetailJobs(event.authorId)
        if ((event.speakerIds.isNotEmpty() || event.participantsIds.isNotEmpty()) &&
            viewModel.users.value.items.isEmpty()
        ) {
            viewModel.loadUsers()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.users.collect { showPeople(event) } }
                launch { viewModel.detailJobs.collect { showAuthorJob() } }
            }
        }
    }

    private fun showEvent(event: Event) {
        binding.detailAuthor.text = event.author ?: getString(R.string.user_fallback)
        binding.detailDate.text = formatServerDate(event.datetime)
        binding.detailContent.text = event.content
        binding.detailLink.text = event.link
        binding.detailLink.visibility = if (event.link.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailLink.setOnClickListener { event.link?.let { openLink(requireContext(), it) } }
        binding.detailParticipants.text = peopleNames(event.participantsIds)
        binding.detailSpeakers.text = peopleNames(event.speakerIds)
        showMapButton(binding.detailMapButton, event.coords)
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
            val uri = Uri.parse("geo:${coords.latitude},${coords.longitude}?q=${coords.latitude},${coords.longitude}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.detail_map_unavailable, Toast.LENGTH_SHORT).show()
            }
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
