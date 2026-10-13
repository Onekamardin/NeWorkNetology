package ru.netology.nework.ui.fragments

import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.viewmodel.ProfileViewModel
import ru.netology.nework.ui.viewmodel.JobOperationState
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
    private var _binding: ScreenCreateJobBinding? = null
    private val binding get() = _binding!!
    private val uiActions get() = activity as? AppUiActions
    private var startDate: Calendar? = null
    private var finishDate: Calendar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ScreenCreateJobBinding.bind(view)
        uiActions?.setScreenTitle(getString(R.string.job_create_title))
        startDate = viewModel.jobStartMillis()?.let { Calendar.getInstance().apply { timeInMillis = it } }
        finishDate = viewModel.jobFinishMillis()?.let { Calendar.getInstance().apply { timeInMillis = it } }
        if (startDate != null) updateDatesLabel()
        binding.jobDatesButton.setOnClickListener { chooseStartDate() }
        binding.jobSaveButton.setOnClickListener { save() }
        binding.jobCancelButton.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.jobOperation.collect { showSaveResult(it) }
            }
        }
    }

    private fun chooseStartDate() {
        val date = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                startDate = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                finishDate = null
                saveDateDraft()
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
            .setPositiveButton(android.R.string.yes) { _, _ -> updateDatesLabel() }
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
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH),
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
        val name = binding.companyInput.text?.toString()?.trim().orEmpty()
        val position = binding.jobPositionInput.text?.toString()?.trim().orEmpty()
        binding.companyLayout.error = if (name.isBlank()) getString(R.string.error_required) else null
        binding.jobPositionLayout.error = if (position.isBlank()) getString(R.string.error_required) else null
        if (name.isBlank() || position.isBlank()) return
        val link = binding.jobLinkInput.text?.toString()?.trim()
        viewModel.createJob(name, position, viewModel.jobStartMillis(), viewModel.jobFinishMillis(), link)
    }

    private fun showSaveResult(state: JobOperationState) {
        when (state) {
            JobOperationState.Idle -> Unit
            JobOperationState.Loading -> {
                binding.jobProgress.visibility = View.VISIBLE
                binding.jobSaveButton.isEnabled = false
                binding.jobCancelButton.isEnabled = false
            }
            JobOperationState.Success -> {
                uiActions?.showMessage(getString(R.string.job_added))
                viewModel.refreshJobs()
                viewModel.clearJobOperationState()
                findNavController().navigateUp()
            }
            is JobOperationState.Error -> {
                binding.jobProgress.visibility = View.GONE
                binding.jobSaveButton.isEnabled = true
                binding.jobCancelButton.isEnabled = true
                uiActions?.showMessage(state.message)
                viewModel.clearJobOperationState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiActions?.setScreenTitle(getString(R.string.job_create_title))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
