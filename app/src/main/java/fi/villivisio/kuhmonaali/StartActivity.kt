package fi.villivisio.kuhmonaali

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_start.*

class StartActivity : AppCompatActivity() {
    private val showInfoScreen = "show_info_screen"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        okBtn.setOnClickListener {
            val dontShowAgain = findViewById<CheckBox>(R.id.checkBox).isChecked
            if (dontShowAgain) {
                this.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE).edit().putBoolean(showInfoScreen, false).apply()
            }
            this.startMainActivity()
        }

        Handler().postDelayed({
            val showInfo = this.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE).getBoolean(showInfoScreen, true)
            if (showInfo) {
                findViewById<ImageView>(R.id.splash).visibility = View.GONE
                findViewById<ImageView>(R.id.splash_logo).visibility = View.GONE
            } else {
                this.startMainActivity()
            }
        }, 3000)
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}