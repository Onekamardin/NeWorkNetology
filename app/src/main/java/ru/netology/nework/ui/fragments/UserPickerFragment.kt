package ru.netology.nework.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ru.netology.nework.R
import ru.netology.nework.databinding.ScreenUserPickerBinding
import ru.netology.nework.ui.adapter.UserSelectionAdapter
import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.common.SaveableScreen
import ru.netology.nework.ui.viewmodel.FeedViewModel

class UserPickerFragment : Fragment(R.layout.screen_user_picker), SaveableScreen {
    private val viewModel: FeedViewModel by activityViewModels()
    private var _binding: ScreenUserPickerBinding? = null
    private val binding get() = _binding!!
    private val selectedIds = mutableSetOf<Long>()
    private val adapter = UserSelectionAdapter(
        isSelected = { it in selectedIds },
        onSelected = { id, selected -> if (selected) selectedIds.add(id) else selectedIds.remove(id) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ids = savedInstanceState?.getLongArray(ARG_SELECTED_IDS)
            ?: arguments?.getLongArray(ARG_SELECTED_IDS)
            ?: longArrayOf()
        selectedIds.addAll(ids.toList())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenUserPickerBinding.bind(view)
        binding.usersList.layoutManager = LinearLayoutManager(requireContext())
        binding.usersList.adapter = adapter
        binding.usersRetry.setOnClickListener { viewModel.loadUsers() }
        binding.usersDone.setOnClickListener { save() }
        if (viewModel.users.value.items.isEmpty() && !viewModel.users.value.loading) viewModel.loadUsers()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.users.collect { state ->
                    adapter.submitList(state.items)
                    binding.usersProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.usersMessage.text = state.error ?: getString(R.string.no_users)
                    binding.usersMessage.visibility = if (!state.loading && (state.error != null || state.items.isEmpty())) View.VISIBLE else View.GONE
                    binding.usersRetry.visibility = if (state.error != null) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun save() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_KEY, selectedIds.toLongArray())
        findNavController().navigateUp()
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppUiActions)?.setScreenTitle(getString(R.string.user_picker_title))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray(ARG_SELECTED_IDS, selectedIds.toLongArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.usersList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_SELECTED_IDS = "selected_user_ids"
        const val RESULT_KEY = "selected_users_result"
    }
}

