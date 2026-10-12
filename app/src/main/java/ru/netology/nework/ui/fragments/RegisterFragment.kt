package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.AppUiActions
import ru.netology.nework.ui.AuthViewModel
import ru.netology.nework.ui.AuthOperation
import ru.netology.nework.ui.AuthState

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.domain.model.AvatarFile
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenRegisterBinding

class RegisterFragment : Fragment(R.layout.screen_register) {
    private val viewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenRegisterBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private var selectedAvatarUri: Uri? = null

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectAvatar(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenRegisterBinding.bind(view)
        uiActions?.setScreenTitle(getString(R.string.register_title))
        binding.selectAvatar.setOnClickListener { avatarPicker.launch("image/*") }
        binding.registerSubmit.setOnClickListener { register() }
        binding.openLogin.setOnClickListener {
            viewModel.clearAuthState()
            findNavController().navigate(R.id.action_global_loginFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.auth.collect { showAuthResult(it) }
            }
        }
    }

    private fun selectAvatar(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri)
        if (mimeType !in setOf("image/jpeg", "image/png")) {
            uiActions?.showMessage(getString(R.string.error_avatar_type))
            return
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val size = runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
                bounds.outWidth to bounds.outHeight
            }
        }.getOrNull()
        val width = size?.first ?: 0
        val height = size?.second ?: 0
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) {
            uiActions?.showMessage(getString(R.string.error_avatar_size))
            return
        }

        selectedAvatarUri = uri
        binding.avatarPreview.setImageURI(uri)
    }

    private fun register() {
        val login = binding.loginInput.text?.toString()?.trim().orEmpty()
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val repeated = binding.repeatPasswordInput.text?.toString().orEmpty()

        binding.loginLayout.error = if (login.isBlank()) getString(R.string.error_required) else null
        binding.nameLayout.error = if (name.isBlank()) getString(R.string.error_required) else null
        binding.passwordLayout.error = if (password.isBlank()) getString(R.string.error_required) else null
        binding.repeatPasswordLayout.error = when {
            repeated.isBlank() -> getString(R.string.error_required)
            repeated != password -> getString(R.string.error_password_mismatch)
            else -> null
        }
        if (login.isBlank() || name.isBlank() || password.isBlank() || repeated != password) return

        val avatarPart = selectedAvatarUri?.let(::readAvatar)
        if (selectedAvatarUri != null && avatarPart == null) {
            uiActions?.showMessage(getString(R.string.register_photo_read_error))
            return
        }
        viewModel.register(login, name, password, avatarPart)
    }

    private fun readAvatar(uri: Uri): AvatarFile? = runCatching {
        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(uri) ?: return@runCatching null
        val extension = if (mimeType == "image/jpeg") "jpg" else if (mimeType == "image/png") "png" else return@runCatching null
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        AvatarFile("avatar.$extension", mimeType, bytes)
    }.getOrNull()

    private fun showAuthResult(state: AuthState) {
        when (state) {
            AuthState.Idle -> Unit
            is AuthState.Loading -> if (state.operation == AuthOperation.REGISTER) {
                binding.registerProgress.visibility = View.VISIBLE
                binding.registerSubmit.isEnabled = false
            }
            is AuthState.Success -> if (state.operation == AuthOperation.REGISTER) {
                findNavController().popBackStack(R.id.feedFragment, false)
                uiActions?.showMessage(getString(R.string.register_success))
                viewModel.clearAuthState()
            }
            is AuthState.HttpError -> if (state.operation == AuthOperation.REGISTER) {
                binding.registerProgress.visibility = View.GONE
                binding.registerSubmit.isEnabled = true
                val message = when (state.code) {
                    400, 403 -> getString(R.string.register_duplicate_login)
                    415 -> getString(R.string.register_photo_format_error)
                    else -> getString(R.string.server_error_code, state.code)
                }
                uiActions?.showMessage(message)
                viewModel.clearAuthState()
            }
            is AuthState.NetworkError -> if (state.operation == AuthOperation.REGISTER) {
                binding.registerProgress.visibility = View.GONE
                binding.registerSubmit.isEnabled = true
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.register_title)
                    .setMessage(getString(R.string.auth_network_error_details, state.detail))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                viewModel.clearAuthState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.register_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
