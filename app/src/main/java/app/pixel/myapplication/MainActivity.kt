// Application package name
package app.pixel.myapplication


// Import necessary Android libraries and dependencies
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.auth.oauth2.GoogleCredentials
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import kotlin.system.exitProcess


// Main Activity Class
class MainActivity : AppCompatActivity() {
    

    // Your google Gemini API key from "https://aistudio.google.com/apikey"
    private var geminiAPIKey= "Replace with yor own google gemini API key"
    //

    /* Declare UI Elements variables */
    private lateinit var userInputTextView: TextView  // Displays recognized user input
    private lateinit var responseTextView: TextView   // Displays the AI response
    private lateinit var settingsCard: MaterialCardView   // Card for settings options
    private lateinit var settingsBtn: Button   // Button to toggle settings card visibility
    private lateinit var darkModeBtn: MaterialButtonToggleGroup   // Button group for dark mode selection
    private lateinit var modeButton: MaterialButtonToggleGroup   // Button group for mode selection
    private lateinit var languageButton: MaterialButtonToggleGroup   // Button group for language selection
    private lateinit var startListeningButton: MaterialCardView
    private lateinit var responseText:String
    private var selectedAudioSource = MediaRecorder.AudioSource.DEFAULT
    private var selectedChannelIn= AudioFormat.CHANNEL_IN_MONO
    private var selectedSample: Int = 48000
    private var selectedBuffer: Int = 1
    private lateinit var listeningIndicator: ProgressBar


    /* Speech and Language Tools */
    private lateinit var speechRecognizer: SpeechRecognizer  // Recognizes user speech
    private lateinit var tts: TextToSpeech  // Converts text to spoken output

    /* Shared Preferences and Language Selection */
    private lateinit var sharedPreferences: SharedPreferences  // Stores user preferences
    private var selectedLanguage = "us"  // Default language
    private val TAG = "MainActivity"  // Tag for logging

    private var isClientMode = true
    private var isRecording = false
    private val inputFileName = "input.mp4"
    private val outputFileName = "output.mp3"
    private var responseFile: File? = null
    private val api: TtsApi by lazy { createRetrofitService() }


    /* Called when the activity is first created */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()  // Hide the action bar
        setContentView(R.layout.activity_main)

        /* Initialize components and configurations */
        initializeUI()
        initializeTextToSpeech()
        checkAudioPermissions()
        applySettings()  // Apply saved dark mode and language settings
        initSpinners()
    }


    /* Functions */
    /* Initialize UI elements and listeners */
    private fun initializeUI() {
        userInputTextView = findViewById(R.id.userInputTextView)
        responseTextView = findViewById(R.id.responseTextView)
        settingsCard = findViewById(R.id.settingsCard)
        settingsBtn = findViewById(R.id.settingsBtn)
        darkModeBtn = findViewById(R.id.darkModeBtn)
        modeButton = findViewById(R.id.modeButton)
        languageButton = findViewById(R.id.languageButton)

        // Initialize SharedPreferences for storing app settings
        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

        listeningIndicator = findViewById(R.id.listeningIndicator)
        // Setup button listeners for main actions
        startListeningButton = findViewById<MaterialCardView>(R.id.startListeningButton).apply {
            setOnClickListener {
                if (isClientMode) {
                    startListening()
                } else {
                    val fileName = "${externalCacheDir?.absolutePath}/$inputFileName"
                    if (isRecording) {
                        val typedValue = TypedValue()
                        theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                        val colorPrimary = typedValue.data
                        startListeningButton.setCardBackgroundColor(colorPrimary)
                        listeningIndicator.visibility = View.GONE
                        stopRecordingAndUpload(fileName)
                        isRecording = false
                    }
                    else {
                        (it as MaterialCardView).setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.red))
                        listeningIndicator.visibility = View.VISIBLE
                        startRecording(fileName)
                        isRecording = true
                    }
                }
            }
        }

        findViewById<MaterialCardView>(R.id.speakResponseButton).setOnClickListener {
            if (isClientMode) {
                speakResponse()
            } else {
                responseFile?.let {
                    playAudio(it)
                }
            }

        }
        findViewById<Button>(R.id.resetButton).setOnClickListener { recreate() }

        // Settings button toggles the visibility of the settings card
        settingsBtn.setOnClickListener { toggleSettingsCard() }
        setupModeButtonListener() // Initialize mode selection listener
        setupLanguageButtonListener() // Initialize language selection listener
        setupDarkModeListener()  // Initialize dark mode toggle listener
    }


    /* Initialize Text-to-Speech with default settings */
    private fun initializeTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale.US  // Default to US English
        }
    }


    /* Request audio recording permission if not already granted */
    private fun checkAudioPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }


    /* Apply saved settings for dark mode and language */
    private fun applySettings() {
        applyDarkMode()  // Set dark mode based on saved preferences
        applySelectedMode()  // Set language based on saved preferences
        applySelectedLanguage()  // Set language based on saved preferences
    }


    /* Start listening for speech input and process recognition results */

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
        }

        // Initialize SpeechRecognizer with custom listener
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListenerAdapter() {

            override fun onReadyForSpeech(params: Bundle?) {
                listeningIndicator.visibility = View.VISIBLE // Show progress bar when ready to listen
                startListeningButton.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.red))
            }

            override fun onEndOfSpeech() {
                listeningIndicator.visibility = View.GONE // Hide progress bar when done listening
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val colorPrimary = typedValue.data
                startListeningButton.setCardBackgroundColor(colorPrimary)
            }

            override fun onError(error: Int) {
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val colorPrimary = typedValue.data
                startListeningButton.setCardBackgroundColor(colorPrimary)
            }

            override fun onResults(results: Bundle?) {
                listeningIndicator.visibility = View.GONE // Hide progress bar after getting results
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0) ?: ""
                userInputTextView.text = recognizedText
                authenticateAndSend(recognizedText)  // Authenticate and send text to Gemini API
            }
        })

        speechRecognizer.startListening(intent)  // Start listening to user speech
    }



    /* Authenticate with Google Gemini API and send recognized text */
    private fun authenticateAndSend(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credentials =
                    GoogleCredentials.fromStream(resources.openRawResource(R.raw.service_account))
                        .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
                credentials.refreshIfExpired()  // Ensure token is valid

                withContext(Dispatchers.Main) {
                    sendToGeminiApi(
                        text,
                        credentials.accessToken.tokenValue
                    )  // Send text to API with token
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading credentials", e)
                responseTextView.text = "Authentication failed: ${e.message}"
            }
        }
    }


    /* Send input text to Google Gemini API and display the response */
    private fun sendToGeminiApi(text: String, accessToken: String) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",  // Google Gemini LLM model version name
            apiKey = geminiAPIKey // Google Gemini API key
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = generativeModel.generateContent(text)  // Generate AI response
                withContext(Dispatchers.Main) {

                    val textView=responseTextView
                    responseTextView.visibility=View.VISIBLE
                    responseText=response.text ?: "No response"

                    if (isClientMode) {
                        speakResponse()
                    } else {
                        responseFile?.let {
                            playAudio(it)
                        }
                    }

                    typeWriterEffect(textView, responseText, 40)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    responseTextView.text =
                        "Error: ${e.message}"  // Display Error message in response text view
                }
            }

        }
    }


    /* Speak the response text using Text-to-Speech */
    private fun speakResponse() {
        tts.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private var recorder: MediaRecorder? = null
    private fun startRecording(filePath: String) {
        recorder = MediaRecorder().apply {
            setAudioSource(selectedAudioSource)
            setAudioSamplingRate(selectedSample)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(filePath)
            prepare()
            start()
        }
    }

    private fun stopRecordingAndUpload(filePath: String) {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        if (filePath.isEmpty())
            return

        uploadAudioFile(File(filePath))
    }

    private fun uploadAudioFile(file: File) {
        val requestBody = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)

        lifecycleScope.launch {
            startListeningButton.isEnabled = false
            val response = api.uploadAudio(part)
            startListeningButton.setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.primary))
            startListeningButton.isEnabled = true
            if (response.isSuccessful) {
                val audioResponse = response.body()
                audioResponse?.let {
                    // Display message
                    userInputTextView.text = it.recognized_text
                    responseTextView.text = it.response

                    // Decode and save the file
                    decodeAndSaveFile(it.file_content_base64)
                }
            } else {
                Toast.makeText(this@MainActivity, "Fail", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun decodeAndSaveFile(base64String: String) {
        try {
            val decodedBytes = Base64.getDecoder().decode(base64String)

            responseFile = File("${externalCacheDir?.absolutePath}/$outputFileName")
            responseFile?.writeBytes(decodedBytes)
        } catch (e: Exception) {
            println("Error decoding file: ${e.message}")
        }
    }

    private fun playAudio(file: File) {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(file.path)
        mediaPlayer.prepare()
        mediaPlayer.start()
    }

    // Function to set up Retrofit service
    private fun createRetrofitService(): TtsApi {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.135:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(TtsApi::class.java)
    }



    /* Toggle the settings card visibility with slide animation */
    private fun toggleSettingsCard() {
        if (settingsCard.visibility == View.GONE) slideDownCard(settingsCard) else slideUpCard(
            settingsCard
        )
    }


    /* Slide down animation for showing settings card */
    private fun slideDownCard(cardView: CardView) {
        val animationSet = AnimationSet(true).apply {
            // Animation: translate from top of the screen to the center and fade in
            addAnimation(TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, -1f, Animation.RELATIVE_TO_SELF, 0f
            ).apply { duration = 250; fillAfter = true })
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 350; fillAfter = true })
        }
        cardView.visibility = View.VISIBLE
        cardView.startAnimation(animationSet)
    }


    /* Slide up animation for hiding settings card */
    private fun slideUpCard(cardView: CardView) {
        val animationSet = AnimationSet(true).apply {
            // Animation: translate to top of the screen and fade out
            addAnimation(TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, -1f
            ).apply { duration = 250; fillAfter = true })
            addAnimation(AlphaAnimation(1f, 0f).apply { duration = 350; fillAfter = true })
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationEnd(animation: Animation) {
                    cardView.visibility = View.GONE
                }

                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
            })
        }
        cardView.startAnimation(animationSet)
    }


    /* Listener to handle dark mode toggle button */
    private fun setupDarkModeListener() {
        darkModeBtn.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.darkOn -> AppCompatDelegate.MODE_NIGHT_YES
                    R.id.darkOff -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.darkAuto -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                sharedPreferences.edit().putString(
                    "dark_mode", when (checkedId) {
                        R.id.darkOn -> "on"
                        R.id.darkOff -> "off"
                        else -> "auto"
                    }
                ).apply()
            }
        }
    }


    /* Apply selected dark mode from shared preferences */
    private fun applyDarkMode() {
        when (sharedPreferences.getString("dark_mode", "auto")) {
            "on" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); darkModeBtn.check(
                    R.id.darkOn
                )
            }

            "off" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); darkModeBtn.check(
                    R.id.darkOff
                )
            }

            "auto" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); darkModeBtn.check(
                    R.id.darkAuto
                )
            }
        }
    }

    /* Listener for mode button selection */
    private fun setupModeButtonListener() {
        modeButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isClientMode = when (checkedId) {
                    R.id.client -> true
                    R.id.server -> false
                    else -> false
                }
            }
        }
    }

    /* Listener for language button selection */
    private fun setupLanguageButtonListener() {
        languageButton.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedLanguage = when (checkedId) {
                    R.id.english -> "us"
                    R.id.persian -> "fa-IR"
                    else -> "us"
                }
                sharedPreferences.edit().putString("selected_language", selectedLanguage).apply()
            }
        }
    }

    /* Apply selected language from shared preferences */
    private fun applySelectedMode() {
        modeButton.check(R.id.client)
    }

    /* Apply selected language from shared preferences */
    private fun applySelectedLanguage() {
        selectedLanguage = sharedPreferences.getString("selected_language", "us") ?: "us"
        languageButton.check(if (selectedLanguage == "fa-IR") R.id.persian else R.id.english)
    }

    // Cleanup resources on activity destruction
    override fun onDestroy() {
        tts.stop()
        tts.shutdown()

        // Check if speechRecognizer is initialized before calling destroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        super.onDestroy()
    }

    /* Back Button actions */
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Check if settings card is visible
        if (settingsCard.visibility == View.VISIBLE) {
            // Slide up the settings card if it’s visible, instead of going back
            slideUpCard(settingsCard)
        } else {
            // If settings card is not visible, show the exit confirmation dialog
            showBackDialog()
        }
    }


    /* Exit app confirmation dialog */
    private fun showBackDialog() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_back_button, null)

        // Build the AlertDialog with the custom view
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setView(dialogView)

        // Show the AlertDialog and hold a reference to dismiss it later
        val alertDialog = alertDialogBuilder.show()

        // Set up button actions in the dialog
        val contactButton = dialogView.findViewById<Button>(R.id.dialog_button_back)
        val dismissButton = dialogView.findViewById<Button>(R.id.dialog_button_dismiss)

        // Handle the "Yes" button to exit the app
        contactButton.setOnClickListener {
            exitButtonAction()
        }

        // Handle the "Not yet!" button to dismiss the dialog
        dismissButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }


    /* Close the app and end all activities */
    private fun exitButtonAction() {
        finishAffinity() // Finish all activities
        exitProcess(0)   // Exit the process to fully close the app
    }

    /////////////////////////////////////////////

    private fun initSpinners() {
        // sampleRate Spinner
        val spinner: Spinner = findViewById(R.id.spinner_sampleRate)
        val sampleRateArray = arrayOf("192000", "96000", "48000", "44100", "22050")
        //



        // audioSource Spinner
        val audioSourceSpinner: Spinner = findViewById(R.id.audioSourceSpinner)
        val audioSourceArray = arrayOf(
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.MIC
        )



        val selectedSampleRate = sharedPreferences.getInt("selectedSampleRate", 2)
        val selectedBufferSize = sharedPreferences.getInt("selectedBufferSize", 4)
        val selectedAudioSourcePosition = sharedPreferences.getInt("selectedAudioSourcePosition", 3)
        val selectedChannelInPosition = sharedPreferences.getInt("selectedChannelInPosition", 1)

        //

        selectedSample = sampleRateArray[selectedSampleRate].toInt()
        selectedAudioSource = audioSourceArray[selectedAudioSourcePosition]


        // Spinner Adapters
        // sampleRate Spinner adapter
        ArrayAdapter.createFromResource(
            this,
            R.array.sampleRate_array,
            R.layout.custom_spinner_item
        ).also { adapter ->
            val defaultSample = selectedSampleRate
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                        selectedSample = sampleRateArray[position].toInt()
                        sharedPreferences.edit().putInt("selectedSampleRate", position).apply()

                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedSample = sampleRateArray[defaultSample].toInt()
                }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice)
            // Apply the adapter to the spinner
            spinner.adapter = adapter
            spinner.setSelection(defaultSample)
        }
        //

        // audioSource adapter
        ArrayAdapter.createFromResource(
            this,
            R.array.audioSource_array,
            R.layout.custom_spinner_item
        ).also { adapter ->
            val defaultSource = selectedAudioSourcePosition
            audioSourceSpinner.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    )

                    {
                            selectedAudioSource = audioSourceArray[position]

                            sharedPreferences.edit().putInt("selectedAudioSourcePosition", position)
                                .apply()

                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedAudioSource = audioSourceArray[defaultSource]
                    }
                }
            adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice)
            // Apply the adapter to the spinner
            audioSourceSpinner.adapter = adapter
            audioSourceSpinner.setSelection(defaultSource)
        }

    }

    private fun typeWriterEffect(textView: TextView, text: String, delay: Long = 50) {
        textView.text = ""  // Clear existing text
        val handler = Handler(Looper.getMainLooper())
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index < text.length) {
                    textView.text = textView.text.toString() + text[index]
                    index++
                    handler.postDelayed(this, delay)
                }
            }
        }
        handler.post(runnable)
    }
}
