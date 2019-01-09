package org.phenoapps.verify

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.LayoutDirection
import android.view.*
import android.widget.*

import com.google.zxing.ResultPoint

import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class CompareActivity : AppCompatActivity() {

    enum class Mode {
        Contains,
        Matches
    }

    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private lateinit var firstEditText: EditText
    private lateinit var secondEditText: EditText
    private lateinit var imageView: ImageView

    private var mMode: Mode = Mode.Matches

    //keeps track of which edit text is being scanned into, at first it is the top edit text.
    private var mFocused: Int = R.id.editText

    private val callback = object : BarcodeCallback {

        override fun barcodeResult(result: BarcodeResult) {

            barcodeScannerView.pause()

            result.text?.let {

                findViewById<EditText>(mFocused).setText(result.text ?: "")

                mFocused = when(mFocused) {
                    R.id.editText -> R.id.editText2
                    else -> R.id.editText
                }

                findViewById<EditText>(mFocused).requestFocus()
            }

            barcodeScannerView.resume()

        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {

        }
    }

    override fun onStart() {
        super.onStart()

        val view = layoutInflater.inflate(R.layout.choice_compare_layout, null)

        val radioGroup = view.findViewById<RadioGroup>(R.id.compare_radio_group)

        val containsRadioButton = radioGroup.findViewById<RadioButton>(R.id.radioButton)
        val matchesRadioButton = radioGroup.findViewById<RadioButton>(R.id.radioButton2)

        containsRadioButton.isChecked = true

        val builder = AlertDialog.Builder(this).apply {

            setView(view)

            setTitle("Choose compare mode:")

            setPositiveButton("OK") { _, _ ->
                when (radioGroup.checkedRadioButtonId) {
                    containsRadioButton.id -> mMode = Mode.Contains
                    matchesRadioButton.id -> mMode = Mode.Matches
                }
            }
        }

        builder.show()

        imageView = findViewById(R.id.imageView)
        firstEditText = findViewById<EditText>(R.id.editText)
        secondEditText = findViewById<EditText>(R.id.editText2)

        firstEditText.setOnClickListener {
            mFocused = R.id.editText
        }

        secondEditText.setOnClickListener {
            mFocused = R.id.editText2
        }

        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {

                if (firstEditText.text.isNotEmpty() && secondEditText.text.isNotEmpty()) {

                    val first = firstEditText.text
                    val second = secondEditText.text
                    when (mMode) {
                        Mode.Contains -> {
                            when {
                                first.contains(second) || second.contains(first) -> {
                                    imageView.setImageResource(R.drawable.ic_checkbox_marked_circle)
                                }
                                else -> imageView.setImageResource(R.drawable.ic_alpha_x_circle)
                            }
                        }
                        Mode.Matches -> {
                            when {
                                firstEditText.text.toString() == secondEditText.text.toString() -> {
                                    imageView.setImageResource(R.drawable.ic_checkbox_marked_circle)
                                } else -> imageView.setImageResource(R.drawable.ic_alpha_x_circle)
                            }
                        }
                    }
                    imageView.visibility = View.VISIBLE
                }
            }

        }

        firstEditText.addTextChangedListener(watcher)
        secondEditText.addTextChangedListener(watcher)

        barcodeScannerView = findViewById(org.phenoapps.verify.R.id.zxing_barcode_scanner)
        barcodeScannerView.barcodeView.cameraSettings.isContinuousFocusEnabled = true
        barcodeScannerView.barcodeView.cameraSettings.isBarcodeSceneModeEnabled = true
        barcodeScannerView.decodeContinuous(callback)

        if (supportActionBar != null) {
            supportActionBar?.title = "Compare Barcodes"
            supportActionBar?.themedContext
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeButtonEnabled(true)
        }

        imageView.setOnClickListener {
            firstEditText.setText("")
            secondEditText.setText("")
            firstEditText.requestFocus()
            imageView.visibility = View.INVISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(org.phenoapps.verify.R.layout.activity_compare)

    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_OK)
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()

        barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()

        barcodeScannerView.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}