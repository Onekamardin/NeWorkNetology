package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.AppUiActions
import ru.netology.nework.ui.EventViewModel
import ru.netology.nework.ui.FeedViewModel
import ru.netology.nework.ui.CreateEventState
import ru.netology.nework.ui.SaveableScreen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.domain.model.User
import ru.netology.nework.databinding.ScreenCreateEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEventFragment : Fragment(R.layout.screen_create_event), SaveableScreen {
    private val viewModel: EventViewModel by activityViewModels()
    private val feedViewModel: FeedViewModel by activityViewModels()
    private var _binding: ScreenCreateEventBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private lateinit var eventDate: Calendar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenCreateEventBinding.bind(view)
        uiActions?.setScreenTitle(getString(R.string.create_event_title))
        eventDate = Calendar.getInstance().apply { timeInMillis = viewModel.eventDateMillis.value }
        feedViewModel.loadUsers()
        updateDateLabel()
        binding.eventDateButton.setOnClickListener { chooseDate() }
        binding.eventSpeakersButton.setOnClickListener { chooseSpeakers(feedViewModel.users.value.items) }
        binding.eventCancelButton.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createEvent.collect { showSaveResult(it) }
            }
        }
    }

    private fun chooseDate() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                eventDate.set(year, month, day)
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        eventDate.set(Calendar.HOUR_OF_DAY, hour)
                        eventDate.set(Calendar.MINUTE, minute)
                        eventDate.set(Calendar.SECOND, 0)
                        eventDate.set(Calendar.MILLISECOND, 0)
                        viewModel.setEventDate(eventDate.timeInMillis)
                        updateDateLabel()
                    },
                    eventDate.get(Calendar.HOUR_OF_DAY),
                    eventDate.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            eventDate.get(Calendar.YEAR),
            eventDate.get(Calendar.MONTH),
            eventDate.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun updateDateLabel() {
        val text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(eventDate.time)
        binding.eventDateButton.text = getString(R.string.event_date_format, text)
    }

    private fun chooseSpeakers(users: List<User>) {
        if (users.isEmpty()) {
            uiActions?.showMessage(getString(R.string.event_speakers_loading))
            feedViewModel.loadUsers()
            return
        }
        val names = users.map {
            it.name?.takeIf(String::isNotBlank) ?: it.login?.takeIf(String::isNotBlank)
            ?: getString(R.string.user_fallback)
        }.toTypedArray()
        val selectedIds = viewModel.selectedSpeakerIds.value
        val checked = BooleanArray(users.size) { users[it].id in selectedIds }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_choose_speakers)
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                viewModel.setSpeakerSelected(users[index].id, isChecked)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val count = viewModel.selectedSpeakerIds.value.size
                binding.eventSpeakersSummary.text = if (count == 0) {
                    getString(R.string.event_speakers_none)
                } else getString(R.string.event_speakers_selected, count)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun save() {
        val content = binding.eventContentInput.text?.toString()?.trim().orEmpty()
        binding.eventContentLayout.error = if (content.isBlank()) getString(R.string.error_required) else null
        if (content.isBlank()) return
        viewModel.createEvent(
            content = content,
            dateMillis = eventDate.timeInMillis,
            offline = binding.eventOffline.isChecked,
            speakerIds = viewModel.selectedSpeakerIds.value.toList(),
            link = binding.eventLinkInput.text?.toString(),
        )
    }

    private fun showSaveResult(state: CreateEventState) {
        when (state) {
            CreateEventState.Idle -> Unit
            CreateEventState.Loading -> {
                binding.eventCreateProgress.visibility = View.VISIBLE
                binding.eventCancelButton.isEnabled = false
            }
            CreateEventState.Success -> {
                uiActions?.showMessage(getString(R.string.event_created))
                viewModel.clearCreateEventState()
                findNavController().navigateUp()
            }
            is CreateEventState.Error -> {
                binding.eventCreateProgress.visibility = View.GONE
                binding.eventCancelButton.isEnabled = true
                uiActions?.showMessage(state.message)
                viewModel.clearCreateEventState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.create_event_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
