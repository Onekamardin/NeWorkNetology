package ru.netology.nework.ui.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import ru.netology.nework.R
import ru.netology.nework.ui.common.AppUiActions
import ru.netology.nework.ui.common.SaveableScreen
import ru.netology.nework.domain.model.Job
import ru.netology.nework.domain.model.Post
import ru.netology.nework.domain.model.Event
import ru.netology.nework.databinding.ActivityMainBinding
import ru.netology.nework.ui.fragments.FeedFragment
import ru.netology.nework.ui.fragments.ProfileFragment
import ru.netology.nework.ui.viewmodel.AuthViewModel
import ru.netology.nework.ui.viewmodel.FeedViewModel
import ru.netology.nework.ui.viewmodel.ProfileViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AppUiActions {
    private val authViewModel: AuthViewModel by viewModels()
    private val feedViewModel: FeedViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var changingBottomNavigationProgrammatically = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        applySystemBarInsets()
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.screen_container) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener { _, _, _ -> invalidateOptionsMenu() }
        binding.toolbar.setNavigationOnClickListener { navController.navigateUp() }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (!changingBottomNavigationProgrammatically) {
                (currentFragment() as? FeedFragment)?.selectTab(item.itemId)
            }
            true
        }
        binding.createButton.setOnClickListener {
            (currentFragment() as? FeedFragment)?.onCreateButtonPressed()
        }
    }

    private fun applySystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val left = binding.root.paddingLeft
        val top = binding.root.paddingTop
        val right = binding.root.paddingRight
        val bottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        val authenticated = authViewModel.isAuthenticated()
        val savingScreen = currentFragment() is SaveableScreen
        menu.findItem(R.id.action_save).isVisible = savingScreen && authenticated
        menu.findItem(R.id.action_login).isVisible = !authenticated
        menu.findItem(R.id.action_register).isVisible = !authenticated
        menu.findItem(R.id.action_profile).isVisible = authenticated
        menu.findItem(R.id.action_logout).isVisible = authenticated
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_login -> {
                authViewModel.clearAuthState()
                navController.navigate(R.id.action_global_loginFragment)
            }
            R.id.action_register -> {
                authViewModel.clearAuthState()
                navController.navigate(R.id.action_global_registerFragment)
            }
            R.id.action_profile -> authViewModel.currentUser()?.let { user ->
                navController.navigate(
                    R.id.action_global_profileFragment,
                    ProfileFragment.createArguments(user),
                )
            }
                ?: showMessage(getString(R.string.profile_unavailable))
            R.id.action_save -> (currentFragment() as? SaveableScreen)?.save()
            R.id.action_logout -> {
                authViewModel.logout()
                profileViewModel.clearProfile()
                invalidateOptionsMenu()
                showMessage(getString(R.string.logout_success))
                navController.popBackStack(R.id.feedFragment, false)
                selectBottomItem(R.id.menu_posts)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun currentFragment(): Fragment? =
        (supportFragmentManager.findFragmentById(R.id.screen_container) as? NavHostFragment)
            ?.childFragmentManager
            ?.primaryNavigationFragment

    override fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showAuthRequired(message: String?) {
        AlertDialog.Builder(this)
            .setMessage(message ?: getString(R.string.auth_required_to_create))
            .setPositiveButton(R.string.login) { _, _ ->
                authViewModel.clearAuthState()
                navController.navigate(R.id.action_global_loginFragment)
            }
            .setNeutralButton(R.string.register) { _, _ ->
                authViewModel.clearAuthState()
                navController.navigate(R.id.action_global_registerFragment)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun confirmDeleteJob(job: Job) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.job_delete_confirm, job.name))
            .setPositiveButton(R.string.delete) { _, _ -> profileViewModel.deleteJob(job.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun confirmDeletePost(post: Post) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.post_delete_confirm))
            .setPositiveButton(R.string.delete) { _, _ -> feedViewModel.deletePost(post) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun confirmDeleteEvent(event: Event) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.event_delete_confirm))
            .setPositiveButton(R.string.delete) { _, _ -> feedViewModel.deleteEvent(event) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun setScreenTitle(title: String, subtitle: String?, showBottomNavigation: Boolean, showCreateButton: Boolean) {
        binding.toolbar.title = title
        binding.toolbar.subtitle = subtitle
        binding.toolbar.navigationIcon = if (navController.currentDestination?.id != R.id.feedFragment) {
            getDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        } else null
        binding.bottomNavigation.visibility = if (showBottomNavigation) View.VISIBLE else View.GONE
        binding.createButton.visibility = if (showCreateButton) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }

    override fun updateFeed(tabId: Int, title: String, showCreateButton: Boolean) {
        selectBottomItem(tabId)
        setScreenTitle(title, showBottomNavigation = true, showCreateButton = showCreateButton)
    }

    private fun selectBottomItem(itemId: Int) {
        if (binding.bottomNavigation.selectedItemId == itemId) return
        changingBottomNavigationProgrammatically = true
        binding.bottomNavigation.selectedItemId = itemId
        changingBottomNavigationProgrammatically = false
    }
}
