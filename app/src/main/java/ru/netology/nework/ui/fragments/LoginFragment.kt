package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.AuthOperation
import ru.netology.nework.ui.viewmodel.AuthState

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
import ru.netology.nework.databinding.ScreenLoginBinding

class LoginFragment : Fragment(R.layout.screen_login) {
    private val viewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenLoginBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenLoginBinding.bind(view)
        uiActions?.setScreenTitle(getString(R.string.login_title))

        binding.loginSubmit.setOnClickListener { login() }
        binding.openRegister.setOnClickListener {
            viewModel.clearAuthState()
            findNavController().navigate(R.id.action_global_registerFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.auth.collect { showAuthResult(it) }
            }
        }
    }

    private fun login() {
        val login = binding.loginInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        binding.loginLayout.error = if (login.isBlank()) getString(R.string.error_required) else null
        binding.passwordLayout.error = if (password.isBlank()) getString(R.string.error_required) else null
        if (login.isNotBlank() && password.isNotBlank()) viewModel.login(login, password)
    }

    private fun showAuthResult(state: AuthState) {
        when (state) {
            AuthState.Idle -> Unit
            is AuthState.Loading -> if (state.operation == AuthOperation.LOGIN) {
                binding.loginProgress.visibility = View.VISIBLE
                binding.loginSubmit.isEnabled = false
            }
            is AuthState.Success -> if (state.operation == AuthOperation.LOGIN) {
                findNavController().popBackStack(R.id.feedFragment, false)
                uiActions?.showMessage(getString(R.string.login_success))
                viewModel.clearAuthState()
            }
            is AuthState.HttpError -> if (state.operation == AuthOperation.LOGIN) {
                binding.loginProgress.visibility = View.GONE
                binding.loginSubmit.isEnabled = true
                val message = if (state.code in listOf(400, 401, 404)) getString(R.string.login_bad_credentials)
                else getString(R.string.server_error_code, state.code)
                uiActions?.showMessage(message)
                viewModel.clearAuthState()
            }
            is AuthState.NetworkError -> if (state.operation == AuthOperation.LOGIN) {
                binding.loginProgress.visibility = View.GONE
                binding.loginSubmit.isEnabled = true
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.login_title)
                    .setMessage(getString(R.string.auth_network_error_details, state.detail))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                viewModel.clearAuthState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.login_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
