package ru.palestra.hide_and_seek_exercise_c.presentation.choose_strategy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.plugins.RxJavaPlugins
import ru.palestra.hide_and_seek_exercise_c.databinding.ActivityChooseBinding
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManager
import ru.palestra.hide_and_seek_exercise_c.presentation.common.dialogs.DialogManagerApi
import ru.palestra.hide_and_seek_exercise_c.presentation.man_hiding.HidingManActivity
import ru.palestra.hide_and_seek_exercise_c.presentation.man_looking.view.LookingManActivity
import timber.log.Timber


/** Экран приложения для выбора режима. */
class ChooseActivity : AppCompatActivity() {

    companion object {

        private const val ARG_FIRST_LAUNCH = "ARG_FIRST_LAUNCH"

        /** Метод создания [Intent] для запуска [ChooseActivity]. */
        fun createIntent(context: Context) =
            Intent(context, ChooseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                putExtra(ARG_FIRST_LAUNCH, false)
            }
    }

    private lateinit var binding: ActivityChooseBinding

    private val dialogManagerApi: DialogManagerApi by lazy {
        DialogManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityChooseBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        binding.txtClientBtn.setOnClickListener {
            startActivity(HidingManActivity.createIntent(this))
        }

        binding.txtServerBtn.setOnClickListener {
            startActivity(LookingManActivity.createIntent(this))
        }

        val extrasLocal = intent.extras
        if (extrasLocal == null || extrasLocal.getBoolean(ARG_FIRST_LAUNCH)) {
            /* Показываем приветственное окно. */
            dialogManagerApi.showOnboardingInfoDialog { /* Nothing. */ }
        }
    }
}