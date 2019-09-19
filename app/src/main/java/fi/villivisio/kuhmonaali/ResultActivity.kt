package fi.villivisio.kuhmonaali

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_result.*
import java.io.File
import kotlin.math.roundToInt
import android.R.attr.label
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.widget.Toast


class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
    }

    override fun onResume() {
        super.onResume()

        val fileUri = intent.getStringExtra("filename")
        val file = File(fileUri)

        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val width = Resources.getSystem().displayMetrics.widthPixels
            val ratio:Float = bitmap.width.toFloat() / bitmap.height.toFloat()
            val height = (width / ratio).roundToInt()
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
            val imageView: ImageView = findViewById(R.id.image)
            imageView.setImageBitmap(scaledBitmap)
        }

        fab_share.setOnClickListener {
            val shareText = "#kuhmonaali #villivisio20v"
            val imageUri = FileProvider.getUriForFile(
                this,
                "fi.villivisio.kuhmonaali.provider",
                file)
            val intent = Intent().apply {
                this.action = Intent.ACTION_SEND
                this.putExtra(Intent.EXTRA_TEXT, shareText)
                this.putExtra(Intent.EXTRA_STREAM, imageUri)
                this.type = "image/jpeg"
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("kuhmonaali", shareText)
            clipboard.primaryClip = clip
            Toast.makeText(this, "Häshtägit leikepöydällä!", Toast.LENGTH_LONG).show()
            startActivity(Intent.createChooser(intent, resources.getText(R.string.share)))
        }
    }
}