package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.PostViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.common.SaveableScreen
import ru.netology.nework.ui.viewmodel.CreatePostState
import ru.netology.nework.ui.viewmodel.ProfileViewModel
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.PostDraft

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.BuildConfig
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenCreatePostBinding
import ru.netology.nework.domain.model.Coordinates

class CreatePostFragment : Fragment(R.layout.screen_create_post), SaveableScreen {
    private val viewModel: PostViewModel by activityViewModels()
    private val feedViewModel: FeedViewModel by activityViewModels()
    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenCreatePostBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private val selectedMentionIds = mutableSetOf<Long>()
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
        _binding = ScreenCreatePostBinding.bind(view)
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
        if (savedEditId != 0L && viewModel.editingPost.value?.id != savedEditId) {
            completed = true
            uiActions?.showMessage(getString(R.string.editor_reopen_required))
            findNavController().navigateUp()
            return
        }
        val editingPost = viewModel.editingPost.value
        uiActions?.setScreenTitle(getString(if (editingPost == null) R.string.create_post_title else R.string.edit_post_title))
        restoreDraft()
        updateLocationButton()
        updateMentionsButton()
        binding.cancelButton.setOnClickListener {
            completed = true
            viewModel.clearEditing()
            viewModel.clearCreatePostState()
            findNavController().navigateUp()
        }
        binding.mentionsButton.setOnClickListener { chooseMentionUsers() }
        binding.postLocationButton.setOnClickListener { openLocationPicker() }
        binding.locationRemoveButton.setOnClickListener {
            selectedCoordinates = null
            updateLocationButton()
        }
        binding.attachmentGalleryButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.attachmentCameraButton.setOnClickListener { openCamera() }
        binding.attachmentAudioVideoButton.setOnClickListener {
            mediaLauncher.launch(arrayOf("audio/*", "video/*"))
        }
        binding.attachmentRemoveButton.setOnClickListener { clearAttachment() }
        updateAttachmentPreview()
        observeLocationResult()
        observeMentionResult()
        updateLocationButton()
        updateMentionsButton()

        pendingAttachmentUri?.let(::loadAttachment)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.createPost.collect { showSaveResult(it) }
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
        val oldAttachment = viewModel.editingPost.value?.attachment?.takeIf { keepExistingAttachment }
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
        binding.contentInput.setText(draft.content)
        binding.postLinkInput.setText(draft.link)
        selectedMentionIds.clear()
        selectedMentionIds.addAll(draft.mentionIds)
        selectedCoordinates = draft.coords
        selectedMedia = draft.media
        keepExistingAttachment = draft.keepAttachment
        updateLocationButton()
        updateMentionsButton()
        updateAttachmentPreview()
    }

    private fun chooseMentionUsers() {
        saveDraft()
        findNavController().navigate(
            R.id.action_global_userPickerFragment,
            bundleOf(UserPickerFragment.ARG_SELECTED_IDS to selectedMentionIds.toLongArray()),
        )
    }

    private fun observeMentionResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        handle.getLiveData<LongArray>(UserPickerFragment.RESULT_KEY).observe(viewLifecycleOwner) { ids ->
            selectedMentionIds.clear()
            selectedMentionIds.addAll(ids.toList())
            updateMentionsButton()
            saveDraft()
            handle.remove<LongArray>(UserPickerFragment.RESULT_KEY)
        }
    }

    private fun updateMentionsButton() {
        binding.mentionsButton.text = if (selectedMentionIds.isEmpty()) {
            getString(R.string.post_choose_mentions)
        } else {
            getString(R.string.post_mentions_selected, selectedMentionIds.size)
        }
    }

    override fun save() {
        if (mediaLoading) {
            uiActions?.showMessage(getString(R.string.attachment_loading))
            return
        }
        val content = binding.contentInput.text?.toString()?.trim().orEmpty()
        binding.contentLayout.error = if (content.isBlank()) getString(R.string.error_required) else null
        if (content.isNotBlank()) {
            val link = binding.postLinkInput.text?.toString()
            viewModel.savePost(
                content = content,
                link = link,
                mentionIds = selectedMentionIds.toList(),
                coords = selectedCoordinates,
                mediaFile = selectedMedia,
                keepExistingAttachment = keepExistingAttachment,
            )
        }
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
        binding.postLocationButton.text = if (coordinates == null) {
            getString(R.string.location_choose)
        } else {
            getString(R.string.location_selected, "%.4f".format(coordinates.latitude), "%.4f".format(coordinates.longitude))
        }
    }

    private fun showSaveResult(state: CreatePostState) {
        val loading = state == CreatePostState.Loading
        binding.createProgress.visibility = if (loading) View.VISIBLE else View.GONE
        listOf(
            binding.contentInput,
            binding.postLinkInput,
            binding.mentionsButton,
            binding.postLocationButton,
            binding.locationRemoveButton,
            binding.attachmentGalleryButton,
            binding.attachmentCameraButton,
            binding.attachmentAudioVideoButton,
            binding.attachmentRemoveButton,
            binding.cancelButton,
        ).forEach { it.isEnabled = !loading }
        when (state) {
            CreatePostState.Idle -> Unit
            CreatePostState.Loading -> Unit
            is CreatePostState.Success -> {
                val wasEditing = viewModel.editingPost.value != null
                completed = true
                uiActions?.showMessage(getString(if (wasEditing) R.string.post_updated else R.string.post_created))
                feedViewModel.updatePost(state.post)
                feedViewModel.loadPosts()
                if (wasEditing) profileViewModel.profile.value.user?.let(profileViewModel::loadProfile)
                clearAttachment()
                viewModel.clearEditing()
                viewModel.clearCreatePostState()
                findNavController().navigateUp()
            }
            is CreatePostState.Error -> {
                uiActions?.showMessage(state.message)
                viewModel.clearCreatePostState()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("editing_id", viewModel.editingPost.value?.id ?: 0L)
        outState.putString("attachment_uri", pendingAttachmentUri?.toString())
        outState.putString("camera_uri", pendingCameraUri?.toString())
        outState.putString("camera_file", pendingCameraFile?.name)
        if (!completed) saveDraft()
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(
            getString(if (viewModel.editingPost.value == null) R.string.create_post_title else R.string.edit_post_title),
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
        viewModel.updateDraft(PostDraft(
            content = screen.contentInput.text?.toString().orEmpty(),
            link = screen.postLinkInput.text?.toString().orEmpty(),
            mentionIds = selectedMentionIds.toSet(), coords = selectedCoordinates,
            media = selectedMedia, keepAttachment = keepExistingAttachment,
        ))
    }
}
