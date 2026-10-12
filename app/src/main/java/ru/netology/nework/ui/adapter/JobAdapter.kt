package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nework.R
import ru.netology.nework.domain.model.Job
import ru.netology.nework.databinding.ItemJobBinding
import ru.netology.nework.ui.formatJobDate

class JobAdapter(private val onDelete: (Job) -> Unit) : ListAdapter<Job, JobAdapter.JobHolder>(Diff) {
    var allowDelete: Boolean = false
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = JobHolder(
        ItemJobBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: JobHolder, position: Int) {
        val job = getItem(position)
        holder.bind(job, allowDelete)
        holder.deleteButton.setOnClickListener { onDelete(job) }
    }

    class JobHolder(private val binding: ItemJobBinding) : RecyclerView.ViewHolder(binding.root) {
        val deleteButton get() = binding.deleteJob

        fun bind(job: Job, allowDelete: Boolean) {
            binding.company.text = job.name
            binding.position.text = job.position
            val start = formatJobDate(job.start)
            val finish = job.finish?.takeIf(String::isNotBlank)?.let(::formatJobDate)
                ?: itemView.context.getString(R.string.job_currently_working)
            binding.period.text = itemView.context.getString(R.string.job_period, start, finish)
            binding.deleteJob.visibility = if (allowDelete) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Job, newItem: Job) = oldItem == newItem
    }
}
