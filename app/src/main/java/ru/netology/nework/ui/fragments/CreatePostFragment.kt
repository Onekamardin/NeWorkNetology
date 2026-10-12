package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.AppUiActions
import ru.netology.nework.ui.PostViewModel
import ru.netology.nework.ui.SaveableScreen
import ru.netology.nework.ui.CreatePostState

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenCreatePostBinding

class CreatePostFragment : Fragment(R.layout.screen_create_post), SaveableScreen {
    private val viewModel: PostViewModel by activityViewModels()
    private var _binding: ScreenCreatePostBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenCreatePostBinding.bind(view)
        uiActions?.setScreenTitle(getString(R.string.create_post_title))
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createPost.collect { showSaveResult(it) }
            }
        }
    }

    override fun save() {
        val content = binding.contentInput.text?.toString()?.trim().orEmpty()
        binding.contentLayout.error = if (content.isBlank()) getString(R.string.error_required) else null
        if (content.isNotBlank()) {
            val link = binding.postLinkInput.text?.toString()
            viewModel.createPost(content, link)
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
        super.onDestroyView()
        _binding = null
    }
}
