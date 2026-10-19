package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.EventViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.CreateEventState
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.EventDraft
import ru.netology.nework.ui.common.SaveableScreen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.withContext
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.ui.utils.EmptyMediaException
import ru.netology.nework.ui.utils.MediaTooLargeException
import ru.netology.nework.ui.utils.UnsupportedMediaException
import ru.netology.nework.ui.utils.readMediaFile
import ru.netology.nework.ui.utils.loadServerImage
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.BuildConfig
import ru.netology.nework.R
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.model.Coordinates
import ru.netology.nework.databinding.ScreenCreateEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEventFragment : Fragment(R.layout.screen_create_event), SaveableScreen {
    private val viewModel: EventViewModel by activityViewModels()
    private val feedViewModel: FeedViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenCreateEventBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private lateinit var eventDate: Calendar
    private var selectedCoordinates: Coordinates? = null
    private var selectedMedia: MediaFile? = null
    private var keepExistingAttachment = true
    private var mediaLoading = false
    private var pendingCameraUri: Uri? = null
    private var pendingAttachmentUri: Uri? = null
    private var pendingCameraFile: File? = null
    private var completed = false
    private var mediaReadTask: CoroutineJob? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::loadAttachment)
    }
    private val mediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::loadAttachment)
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            loadAttachment(uri)
        } else {
            pendingCameraFile?.delete()
            pendingCameraUri = null
            pendingCameraFile = null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenCreateEventBinding.bind(view)
        if (savedInstanceState != null) {
            pendingAttachmentUri = savedInstanceState.getString("attachment_uri")?.let(Uri::parse)
            pendingCameraUri = savedInstanceState.getString("camera_uri")?.let(Uri::parse)
            pendingCameraFile = savedInstanceState.getString("camera_file")?.let {
                File(File(requireContext().cacheDir, "media"), it)
            }
        }
        if (!authViewModel.isAuthenticated()) {
            findNavController().navigateUp()
            uiActions?.showAuthRequired()
            return
        }
        val savedEditId = savedInstanceState?.getLong("editing_id") ?: 0L
        if (savedEditId != 0L && viewModel.editingEvent.value?.id != savedEditId) {
            completed = true
            uiActions?.showMessage(getString(R.string.editor_reopen_required))
            findNavController().navigateUp()
            return
        }
        eventDate = Calendar.getInstance().apply { timeInMillis = viewModel.eventDateMillis.value }
        val editingEvent = viewModel.editingEvent.value
        uiActions?.setScreenTitle(getString(if (editingEvent == null) R.string.create_event_title else R.string.edit_event_title))
        restoreDraft()
        feedViewModel.loadUsers()
        updateDateLabel()
        binding.eventDateButton.setOnClickListener { chooseDate() }
        binding.eventLocationButton.setOnClickListener { openLocationPicker() }
        binding.locationRemoveButton.setOnClickListener {
            selectedCoordinates = null
            updateLocationButton()
        }
        observeLocationResult()
        updateLocationButton()
        binding.eventSpeakersButton.setOnClickListener { chooseSpeakers(feedViewModel.users.value.items) }
        updateSpeakersSummary()
        binding.eventCancelButton.setOnClickListener {
            completed = true
            viewModel.resetEventDraft()
            viewModel.clearCreateEventState()
            findNavController().navigateUp()
        }
        binding.attachmentGalleryButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.attachmentCameraButton.setOnClickListener { openCamera() }
        binding.attachmentAudioVideoButton.setOnClickListener {
            mediaLauncher.launch(arrayOf("audio/*", "video/*"))
        }
        binding.attachmentRemoveButton.setOnClickListener { clearAttachment() }
        updateAttachmentPreview()

        pendingAttachmentUri?.let(::loadAttachment)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.createEvent.collect { showSaveResult(it) }
            }
        }
    }

    private fun openCamera() {
        try {
            val mediaDirectory = File(requireContext().cacheDir, "media")
            mediaDirectory.mkdirs()
            val file = File.createTempFile("nework_photo_", ".jpg", mediaDirectory)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file,
            )
            pendingCameraFile = file
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            uiActions?.showMessage(getString(R.string.attachment_camera_error))
        }
    }

    private fun loadAttachment(uri: Uri) {
        val context = requireContext()
        mediaReadTask?.cancel()
        pendingAttachmentUri = uri
        mediaLoading = true
        updateAttachmentPreview()
        mediaReadTask = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readMediaFile(context, uri) }
            }
            mediaLoading = false
            pendingAttachmentUri = null
            if (uri == pendingCameraUri) {
                pendingCameraFile?.delete()
                pendingCameraUri = null
                pendingCameraFile = null
            }
            result.onSuccess { mediaFile ->
                selectedMedia = mediaFile
                updateAttachmentPreview()
            }.onFailure { error ->
                val messageId = when (error) {
                    is MediaTooLargeException -> R.string.attachment_too_large
                    is UnsupportedMediaException -> R.string.attachment_type_error
                    is EmptyMediaException -> R.string.attachment_read_error
                    else -> R.string.attachment_read_error
                }
                updateAttachmentPreview()
                uiActions?.showMessage(getString(messageId))
            }
        }
    }

    private fun updateAttachmentPreview() {
        val screen = _binding ?: return
        val mediaFile = selectedMedia
        val oldAttachment = viewModel.editingEvent.value?.attachment?.takeIf { keepExistingAttachment }
        val imageSelected = mediaFile?.mimeType?.startsWith("image/") == true ||
            (mediaFile == null && oldAttachment?.type?.let { it.equals("IMAGE", true) || it.startsWith("image/", true) } == true)
        screen.attachmentName.visibility = if (mediaFile == null && oldAttachment == null && !mediaLoading) View.GONE else View.VISIBLE
        screen.attachmentRemoveButton.visibility = if (mediaFile == null && oldAttachment == null) View.GONE else View.VISIBLE
        screen.attachmentPreview.visibility = if (imageSelected) View.VISIBLE else View.GONE
        screen.attachmentName.text = when {
            mediaLoading -> getString(R.string.attachment_loading)
            mediaFile != null -> getString(R.string.attachment_selected, mediaFile.fileName)
            oldAttachment != null -> getString(R.string.attachment_selected, oldAttachment.url.substringAfterLast('/').substringBefore('?'))
            else -> getString(R.string.attachment_none)
        }
        if (mediaFile?.mimeType?.startsWith("image/") == true) {
            Glide.with(this).load(mediaFile.bytes).centerCrop().into(screen.attachmentPreview)
        } else if (imageSelected && oldAttachment != null) {
            screen.attachmentPreview.loadServerImage(oldAttachment.url)
        } else {
            Glide.with(this).clear(screen.attachmentPreview)
        }
    }

    private fun clearAttachment() {
        mediaReadTask?.cancel()
        mediaReadTask = null
        pendingAttachmentUri = null
        mediaLoading = false
        selectedMedia = null
        keepExistingAttachment = false
        updateAttachmentPreview()
    }

    private fun restoreDraft() {
        val draft = viewModel.draft
        binding.eventContentInput.setText(draft.content)
        binding.eventLinkInput.setText(draft.link)
        binding.eventOffline.isChecked = draft.offline
        binding.eventOnline.isChecked = !binding.eventOffline.isChecked
        selectedCoordinates = draft.coords
        selectedMedia = draft.media
        keepExistingAttachment = draft.keepAttachment
        updateLocationButton()
        updateDateLabel()
        updateSpeakersSummary()
        updateAttachmentPreview()
    }

    private fun chooseDate() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val selectedDate = (eventDate.clone() as Calendar).apply { set(year, month, day) }
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        selectedDate.set(Calendar.HOUR_OF_DAY, hour)
                        selectedDate.set(Calendar.MINUTE, minute)
                        selectedDate.set(Calendar.SECOND, 0)
                        selectedDate.set(Calendar.MILLISECOND, 0)
                        eventDate = selectedDate
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

    private fun updateSpeakersSummary() {
        val count = viewModel.selectedSpeakerIds.value.size
        binding.eventSpeakersSummary.text = if (count == 0) {
            getString(R.string.event_speakers_none)
        } else getString(R.string.event_speakers_selected, count)
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
        val selectedIds = viewModel.selectedSpeakerIds.value.toMutableSet()
        val checked = BooleanArray(users.size) { users[it].id in selectedIds }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_choose_speakers)
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                if (isChecked) selectedIds.add(users[index].id) else selectedIds.remove(users[index].id)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                users.forEach { viewModel.setSpeakerSelected(it.id, it.id in selectedIds) }
                updateSpeakersSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun save() {
        if (mediaLoading) {
            uiActions?.showMessage(getString(R.string.attachment_loading))
            return
        }
        val content = binding.eventContentInput.text?.toString()?.trim().orEmpty()
        binding.eventContentLayout.error = if (content.isBlank()) getString(R.string.error_required) else null
        if (content.isBlank()) return
        viewModel.saveEvent(
            content = content,
            dateMillis = eventDate.timeInMillis,
            offline = binding.eventOffline.isChecked,
            speakerIds = viewModel.selectedSpeakerIds.value.toList(),
            link = binding.eventLinkInput.text?.toString(),
            coords = selectedCoordinates,
            mediaFile = selectedMedia,
            keepExistingAttachment = keepExistingAttachment,
        )
    }

    private fun openLocationPicker() {
        saveDraft()
        val coordinates = selectedCoordinates?.let { "${it.latitude},${it.longitude}" }.orEmpty()
        findNavController().navigate(
            R.id.action_global_locationPickerFragment,
            bundleOf("initial_coordinates" to coordinates),
        )
    }

    private fun observeLocationResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<String>(LocationPickerFragment.RESULT_KEY).observe(viewLifecycleOwner) { value ->
            val parts = value.split(",")
            if (parts.size == 2) {
                val latitude = parts[0].toDoubleOrNull()
                val longitude = parts[1].toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    selectedCoordinates = Coordinates(latitude, longitude)
                    updateLocationButton()
                }
            }
            handle.remove<String>(LocationPickerFragment.RESULT_KEY)
        }
    }

    private fun updateLocationButton() {
        val coordinates = selectedCoordinates
        binding.locationRemoveButton.visibility = if (coordinates == null) View.GONE else View.VISIBLE
        binding.eventLocationButton.text = if (coordinates == null) {
            getString(R.string.location_choose)
        } else {
            getString(R.string.location_selected, "%.4f".format(coordinates.latitude), "%.4f".format(coordinates.longitude))
        }
    }

    private fun showSaveResult(state: CreateEventState) {
        val loading = state == CreateEventState.Loading
        binding.eventCreateProgress.visibility = if (loading) View.VISIBLE else View.GONE
        listOf(
            binding.eventContentInput,
            binding.eventLinkInput,
            binding.eventOnline,
            binding.eventOffline,
            binding.eventDateButton,
            binding.eventLocationButton,
            binding.locationRemoveButton,
            binding.eventSpeakersButton,
            binding.attachmentGalleryButton,
            binding.attachmentCameraButton,
            binding.attachmentAudioVideoButton,
            binding.attachmentRemoveButton,
            binding.eventCancelButton,
        ).forEach { it.isEnabled = !loading }
        when (state) {
            CreateEventState.Idle -> Unit
            CreateEventState.Loading -> Unit
            is CreateEventState.Success -> {
                val wasEditing = viewModel.editingEvent.value != null
                completed = true
                uiActions?.showMessage(getString(if (wasEditing) R.string.event_updated else R.string.event_created))
                feedViewModel.updateEvent(state.event)
                feedViewModel.loadEvents()
                clearAttachment()
                viewModel.resetEventDraft()
                viewModel.clearCreateEventState()
                findNavController().navigateUp()
            }
            is CreateEventState.Error -> {
                uiActions?.showMessage(state.message)
                viewModel.clearCreateEventState()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("editing_id", viewModel.editingEvent.value?.id ?: 0L)
        outState.putString("attachment_uri", pendingAttachmentUri?.toString())
        outState.putString("camera_uri", pendingCameraUri?.toString())
        outState.putString("camera_file", pendingCameraFile?.name)
        if (!completed) saveDraft()
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(
            getString(if (viewModel.editingEvent.value == null) R.string.create_event_title else R.string.edit_event_title),
        )
    }

    override fun onDestroyView() {
        mediaReadTask?.cancel()
        mediaReadTask = null
        if (!completed) saveDraft()
        mediaLoading = false
        super.onDestroyView()
        _binding = null
    }

    private fun saveDraft() {
        val screen = _binding ?: return
        viewModel.updateDraft(EventDraft(
            content = screen.eventContentInput.text?.toString().orEmpty(),
            link = screen.eventLinkInput.text?.toString().orEmpty(), offline = screen.eventOffline.isChecked,
            coords = selectedCoordinates, media = selectedMedia, keepAttachment = keepExistingAttachment,
        ))
    }
}
