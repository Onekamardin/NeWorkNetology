package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.ProfileViewModel
import ru.netology.nework.ui.viewmodel.JobOperationState
import ru.netology.nework.ui.viewmodel.JobOperation
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.common.SaveableScreen

import android.app.DatePickerDialog
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
import ru.netology.nework.databinding.ScreenCreateJobBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateJobFragment : Fragment(R.layout.screen_create_job), SaveableScreen {
    private val viewModel: ProfileViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private var _binding: ScreenCreateJobBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private var startDate: Calendar? = null
    private var finishDate: Calendar? = null
    private var completed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenCreateJobBinding.bind(view)
        if (!authViewModel.isAuthenticated()) {
            findNavController().navigateUp()
            uiActions?.showAuthRequired()
            return
        }
        val savedEditId = savedInstanceState?.getLong("editing_id") ?: 0L
        if (savedEditId != 0L && viewModel.editingJob.value?.id != savedEditId) {
            completed = true
            uiActions?.showMessage(getString(R.string.editor_reopen_required))
            findNavController().navigateUp()
            return
        }
        val draft = viewModel.jobDraft.value
        binding.companyInput.setText(draft.name)
        binding.jobPositionInput.setText(draft.position)
        binding.jobLinkInput.setText(draft.link)
        updateTitle()
        startDate = viewModel.jobStartMillis()?.let { Calendar.getInstance().apply { timeInMillis = it } }
        finishDate = viewModel.jobFinishMillis()?.let { Calendar.getInstance().apply { timeInMillis = it } }
        if (startDate != null) updateDatesLabel()
        binding.jobDatesButton.setOnClickListener { chooseStartDate() }
        binding.jobSaveButton.setOnClickListener { save() }
        binding.jobCancelButton.setOnClickListener {
            completed = true
            viewModel.resetJobDraft()
            findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.jobOperation.collect { showSaveResult(it) }
            }
        }
    }

    private fun chooseStartDate() {
        val date = startDate ?: Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                startDate = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                saveDateDraft()
                updateDatesLabel()
                askAboutFinishDate()
            },
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun askAboutFinishDate() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.job_current_choice)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                finishDate = null
                saveDateDraft()
                updateDatesLabel()
            }
            .setNegativeButton(R.string.job_choose_finish) { _, _ -> chooseFinishDate() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseFinishDate() {
        val start = startDate ?: return
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val finish = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (finish.before(start)) {
                    uiActions?.showMessage(getString(R.string.job_finish_before_start))
                } else {
                    finishDate = finish
                    saveDateDraft()
                    updateDatesLabel()
                }
            },
            (finishDate ?: start).get(Calendar.YEAR),
            (finishDate ?: start).get(Calendar.MONTH),
            (finishDate ?: start).get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun saveDateDraft() {
        viewModel.setJobPeriod(startDate?.timeInMillis, finishDate?.timeInMillis)
    }

    private fun updateDatesLabel() {
        val start = startDate ?: return
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val startText = formatter.format(start.time)
        val finishText = finishDate?.let { formatter.format(it.time) }
            ?: getString(R.string.job_currently_working)
        binding.jobDatesSummary.text = getString(R.string.job_dates_summary, startText, finishText)
    }

    override fun save() {
        if (!authViewModel.isAuthenticated()) {
            uiActions?.showAuthRequired()
            return
        }
        val name = binding.companyInput.text?.toString()?.trim().orEmpty()
        val position = binding.jobPositionInput.text?.toString()?.trim().orEmpty()
        binding.companyLayout.error = if (name.isBlank()) getString(R.string.error_required) else null
        binding.jobPositionLayout.error = if (position.isBlank()) getString(R.string.error_required) else null
        if (name.isBlank() || position.isBlank()) return
        val link = binding.jobLinkInput.text?.toString()?.trim()
        viewModel.updateJobDraft(name, position, link.orEmpty())
        viewModel.saveJob(name, position, viewModel.jobStartMillis(), viewModel.jobFinishMillis(), link)
    }

    private fun showSaveResult(state: JobOperationState) {
        val loading = state == JobOperationState.Loading
        binding.jobProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.jobSaveButton.isEnabled = !loading
        binding.jobCancelButton.isEnabled = !loading
        listOf(binding.companyInput, binding.jobPositionInput, binding.jobLinkInput, binding.jobDatesButton)
            .forEach { it.isEnabled = !loading }
        when (state) {
            JobOperationState.Idle -> Unit
            JobOperationState.Loading -> Unit
            is JobOperationState.Success -> {
                if (state.operation != JobOperation.DELETED) {
                    val message = if (state.operation == JobOperation.UPDATED) R.string.job_updated else R.string.job_added
                    uiActions?.showMessage(getString(message))
                    completed = true
                    viewModel.resetJobDraft()
                    viewModel.refreshJobs()
                    findNavController().navigateUp()
                }
            }
            is JobOperationState.Error -> {
                uiActions?.showMessage(state.message)
                viewModel.clearJobOperationState()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("editing_id", viewModel.editingJob.value?.id ?: 0L)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
    }

    private fun updateTitle() {
        val title = if (viewModel.editingJob.value == null) R.string.job_create_title else R.string.job_edit_title
        uiActions?.setScreenTitle(getString(title))
    }

    override fun onDestroyView() {
        if (!completed) _binding?.let {
            viewModel.updateJobDraft(
                it.companyInput.text?.toString().orEmpty(),
                it.jobPositionInput.text?.toString().orEmpty(),
                it.jobLinkInput.text?.toString().orEmpty(),
            )
        }
        super.onDestroyView()
        _binding = null
    }
}
