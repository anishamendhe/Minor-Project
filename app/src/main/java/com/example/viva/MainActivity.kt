package com.example.viva

import android.content.res.Resources
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var numQuestionsEditText: EditText
    private lateinit var generateButton: Button
    private lateinit var questionsList: List<String>
    private lateinit var questionsTextView: TextView
    private lateinit var subjectSpinner: Spinner
    private lateinit var textToSpeech: TextToSpeech

    private val subjectFiles = mapOf(
        "Operating System" to R.raw.questions_os,
        //"Computer Network" to R.raw.questions_cn
        // Add more subjects and corresponding file paths as needed
    )

    private var currentQuestionIndex = 0
    private val selectedQuestions = mutableListOf<String>()
    private val ttsTimeout = 180000L // 3 minutes in milliseconds
    private lateinit var questionTimer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        numQuestionsEditText = findViewById(R.id.numQuestionsEditText)
        generateButton = findViewById(R.id.generateButton)
        questionsTextView = findViewById(R.id.questionsTextView)
        subjectSpinner = findViewById(R.id.subjectSpinner)

        // Initialize the TextToSpeech object
        textToSpeech = TextToSpeech(this, this)

        // Populate the spinner with subject options
        val subjectOptions = resources.getStringArray(R.array.subject_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjectOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = adapter

        generateButton.setOnClickListener {
            generateRandomQuestionsInOrder()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set the language for TTS (you can change it to your preferred language)
            val language = Locale.US
            val result = textToSpeech.setLanguage(language)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Handle TTS language not supported
            } else {
                // Adjust TTS settings for natural speech
                val slowRate = 0.7f // Adjust the rate as needed (0.5f to 2.0f, where 1.0f is the default)
                val normalPitch = 1.0f // Adjust the pitch as needed (1.0f is the default)
                textToSpeech.setSpeechRate(slowRate)
                textToSpeech.setPitch(normalPitch)
            }
        } else {
            // Handle TTS initialization error
        }
    }

    private fun askNextQuestion() {
        if (currentQuestionIndex < selectedQuestions.size) {
            val questionToAsk = selectedQuestions[currentQuestionIndex]
            textToSpeech.speak(questionToAsk, TextToSpeech.QUEUE_FLUSH, null, null)
            currentQuestionIndex++

            // Start the timer after speaking the question
            startQuestionTimer()
        } else {
            // All questions have been asked
            currentQuestionIndex = 0
        }
    }

    private fun startQuestionTimer() {
        questionTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update the timer display, e.g., timerTextView.text = "Time left: ${millisUntilFinished / 1000} seconds"
            }

            override fun onFinish() {
                // Timer finished, ask the next question
                askNextQuestion()
            }
        }
        questionTimer.start()
    }

    private fun generateRandomQuestionsInOrder() {
        val selectedSubject = subjectSpinner.selectedItem.toString()
        val numQuestions = numQuestionsEditText.text.toString().toIntOrNull()

        val fileId = subjectFiles[selectedSubject]

        if (fileId != null && numQuestions != null && numQuestions > 0) {
            val random = Random()

            val inputStream = resources.openRawResource(fileId)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val allQuestions = reader.readLines()

            while (selectedQuestions.size < numQuestions) {
                val randomIndex = random.nextInt(allQuestions.size)
                val selectedQuestion = allQuestions[randomIndex]
                selectedQuestions.add("${selectedQuestions.size + 1}. $selectedQuestion")
            }

            // Update the TextView with the selected questions
            val selectedQuestionsString = selectedQuestions.joinToString("\n")
            questionsTextView.text = selectedQuestionsString

            // Start asking questions
            askNextQuestion()
        } else {
            // Handle invalid input (e.g., show a message to the user)
            questionsTextView.text = "Invalid input or not enough questions available."
        }
    }

    override fun onDestroy() {
        // Shutdown TextToSpeech when the activity is destroyed
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()

        // Cancel the question timer to prevent leaks
        if (::questionTimer.isInitialized) {
            questionTimer.cancel()
        }

        super.onDestroy()
    }
}
