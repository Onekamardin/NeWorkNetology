package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.PostViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.common.SaveableScreen
import ru.netology.nework.ui.viewmodel.CreatePostState

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.netology.nework.domain.model.MediaFile
import ru.netology.nework.ui.utils.EmptyMediaException
import ru.netology.nework.ui.utils.MediaTooLargeException
import ru.netology.nework.ui.utils.UnsupportedMediaException
import ru.netology.nework.ui.utils.readMediaFile
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
import ru.netology.nework.databinding.ScreenCreatePostBinding
import ru.netology.nework.domain.model.User
import ru.netology.nework.domain.model.Coordinates

class CreatePostFragment : Fragment(R.layout.screen_create_post), SaveableScreen {
    private val viewModel: PostViewModel by activityViewModels()
    private val feedViewModel: FeedViewModel by activityViewModels()
    private var _binding: ScreenCreatePostBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private val selectedMentionIds = mutableSetOf<Long>()
    private var selectedCoordinates: Coordinates? = null
    private var selectedMedia: MediaFile? = null
    private var mediaLoading = false
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null

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
        uiActions?.setScreenTitle(getString(R.string.create_post_title))
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.mentionsButton.setOnClickListener { chooseMentionUsers() }
        binding.postLocationButton.setOnClickListener { openLocationPicker() }
        binding.attachmentGalleryButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.attachmentCameraButton.setOnClickListener { openCamera() }
        binding.attachmentAudioVideoButton.setOnClickListener {
            mediaLauncher.launch(arrayOf("audio/*", "video/*"))
        }
        binding.attachmentRemoveButton.setOnClickListener { clearAttachment() }
        updateAttachmentPreview()
        observeLocationResult()
        updateLocationButton()
        updateMentionsButton()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        mediaLoading = true
        updateAttachmentPreview()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readMediaFile(context, uri) }
            }
            mediaLoading = false
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
        screen.attachmentName.visibility = if (mediaFile == null && !mediaLoading) View.GONE else View.VISIBLE
        screen.attachmentRemoveButton.visibility = if (mediaFile == null) View.GONE else View.VISIBLE
        screen.attachmentPreview.visibility = if (mediaFile?.mimeType?.startsWith("image/") == true) {
            View.VISIBLE
        } else {
            View.GONE
        }
        screen.attachmentName.text = when {
            mediaLoading -> getString(R.string.attachment_loading)
            mediaFile != null -> getString(R.string.attachment_selected, mediaFile.fileName)
            else -> getString(R.string.attachment_none)
        }
        if (mediaFile?.mimeType?.startsWith("image/") == true) {
            Glide.with(this).load(mediaFile.bytes).centerCrop().into(screen.attachmentPreview)
        } else {
            Glide.with(this).clear(screen.attachmentPreview)
        }
    }

    private fun clearAttachment() {
        mediaLoading = false
        selectedMedia = null
        updateAttachmentPreview()
    }

    private fun chooseMentionUsers() {
        val users = feedViewModel.users.value.items
        if (users.isEmpty()) {
            uiActions?.showMessage(getString(R.string.post_mentions_loading))
            feedViewModel.loadUsers()
            return
        }

        val names = users.map { userName(it) }.toTypedArray()
        val checkedUsers = BooleanArray(users.size) { users[it].id in selectedMentionIds }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.post_choose_mentions)
            .setMultiChoiceItems(names, checkedUsers) { _, index, checked ->
                val userId = users[index].id
                if (checked) selectedMentionIds.add(userId) else selectedMentionIds.remove(userId)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> updateMentionsButton() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun userName(user: User): String = user.name?.takeIf(String::isNotBlank)
        ?: user.login?.takeIf(String::isNotBlank)?.let { "@$it" }
        ?: getString(R.string.detail_user_fallback, user.id)

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
            viewModel.createPost(
                content = content,
                link = link,
                mentionIds = selectedMentionIds.toList(),
                coords = selectedCoordinates,
                mediaFile = selectedMedia,
            )
        }
    }

    private fun openLocationPicker() {
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
        binding.postLocationButton.text = if (coordinates == null) {
            getString(R.string.location_choose)
        } else {
            getString(R.string.location_selected, "%.4f".format(coordinates.latitude), "%.4f".format(coordinates.longitude))
        }
    }

    private fun showSaveResult(state: CreatePostState) {
        when (state) {
            CreatePostState.Idle -> Unit
            CreatePostState.Loading -> {
                binding.createProgress.visibility = View.VISIBLE
                binding.cancelButton.isEnabled = false
            }
            CreatePostState.Success -> {
                uiActions?.showMessage(getString(R.string.post_created))
                clearAttachment()
                viewModel.clearCreatePostState()
                findNavController().navigateUp()
            }
            is CreatePostState.Error -> {
                binding.createProgress.visibility = View.GONE
                binding.cancelButton.isEnabled = true
                uiActions?.showMessage(state.message)
                viewModel.clearCreatePostState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.create_post_title))
    }

    override fun onDestroyView() {
        mediaLoading = false
        super.onDestroyView()
        _binding = null
    }
}
