package com.example.viva

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.jlibrosa.audio.JLibrosa
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.IntBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var numQuestionsEditText: EditText
    private lateinit var generateButton: Button
    private lateinit var questionsList: List<String>
    private lateinit var questionsTextView: TextView
    private lateinit var subjectSpinner: Spinner
    lateinit var textToSpeech: TextToSpeech
    private lateinit var resultTextView: TextView
    private lateinit var Button: Button
    private lateinit var mediaRecorder: MediaRecorder
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var modelInterpreter: Interpreter? = null



    private val subjectFiles = mapOf(
        "Operating System" to R.raw.questions_os,
        "Computer Network" to R.raw.questions_cn
        // Add more subjects and corresponding file paths as needed
    )

    private var currentQuestionIndex = 0
    private val selectedQuestions = mutableListOf<String>()
    private val ttsTimeout = 180000L // 3 minutes in milliseconds
    private lateinit var questionTimer: CountDownTimer
    private lateinit var tfLiteModel: MappedByteBuffer
    private lateinit var tfLite: Interpreter
    private lateinit var transcribeButton: Button
    private lateinit var playAudioButton: Button
    private lateinit var resultTextview: TextView

    private val mediaPlayer = MediaPlayer()

    private val TAG = "TfLiteASRDemo"
    private val SAMPLE_RATE = 16000
    private val DEFAULT_AUDIO_DURATION = -1
    private val wavFilename = "deep.wav"
    private val TFLITE_FILE = "model.tflite"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(this));
        }
        val py = Python.getInstance()
        val module = py.getModule("script")

        val num =module["number"]?.toInt()
        println("The value of num is $num")

        val text =module["text"]?.toString()
        println("The value of text is $text")

        val fact=module["factorial"]
        val a= fact?.call(5)
        println("The value of a is $a")

        val jLibrosa = JLibrosa()

        numQuestionsEditText = findViewById(R.id.numQuestionsEditText)
        playAudioButton = findViewById(R.id.playAudioButton)
        generateButton = findViewById(R.id.generateButton)
        questionsTextView = findViewById(R.id.questionsTextView)
        subjectSpinner = findViewById(R.id.subjectSpinner)
        resultTextView = findViewById(R.id.resultTextView)

        // Initialize the TextToSpeech object
        textToSpeech = TextToSpeech(this, this)


        playAudioButton.setOnClickListener {
            try {
                assets.openFd(wavFilename).use { assetFileDescriptor ->
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(
                        assetFileDescriptor.fileDescriptor,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.length
                    )
                    mediaPlayer.prepare()
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Error playing audio")
            }
            mediaPlayer.start()
        }

        transcribeButton = findViewById(R.id.recognizeButton)
        resultTextview = findViewById(R.id.resultTextView)
        transcribeButton.setOnClickListener {
            try {
                val audioFeatureValues = jLibrosa.loadAndRead(
                    copyWavFileToCache(wavFilename),
                    SAMPLE_RATE,
                    DEFAULT_AUDIO_DURATION
                )

                val inputArray = arrayOf(audioFeatureValues)
                val outputBuffer = IntBuffer.allocate(2000)

                val outputMap: MutableMap<Int, Any> = HashMap()
                outputMap[0] = outputBuffer

                tfLiteModel = loadModelFile(assets, TFLITE_FILE)
                val tfLiteOptions = Interpreter.Options()
                tfLite = Interpreter(tfLiteModel, tfLiteOptions)
                tfLite.resizeInput(0, intArrayOf(audioFeatureValues.size))

                tfLite.runForMultipleInputsOutputs(inputArray, outputMap)

                val outputSize = tfLite.getOutputTensor(0).shape()[0]
                val outputArray = IntArray(outputSize)
                outputBuffer.rewind()
                outputBuffer.get(outputArray)
                val finalResult = StringBuilder()
                for (i in 0 until outputSize) {
                    val c = outputArray[i].toChar()
                    if (outputArray[i] != 0) {
                        finalResult.append(outputArray[i].toChar())
                    }
                }
                resultTextview.text = finalResult.toString()
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Error transcribing")
            }
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
        mediaRecorder = MediaRecorder()

//        // Set up click listener for the recordButton
//        recordButton.setOnClickListener {
//            if (isRecording) {
//                stopRecording()
//            } else {
//                startRecording { audioData ->
//                    // Pass the audio data to another function
//                    processAudioData(audioData)
//                }
//            }
//        }


        // Populate the spinner with subject options
        val subjectOptions = resources.getStringArray(R.array.subject_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjectOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = adapter

        generateButton.setOnClickListener {
            generateRandomQuestionsInOrder()
        }


    }


    private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun copyWavFileToCache(wavFilename: String): String {
        val destinationFile = File(cacheDir, wavFilename)
        if (!destinationFile.exists()) {
            try {
                assets.open(wavFilename).use { inputStream ->
                    val inputStreamSize = inputStream.available()
                    val buffer = ByteArray(inputStreamSize)
                    inputStream.read(buffer)
                    FileOutputStream(destinationFile).use { fileOutputStream ->
                        fileOutputStream.write(buffer)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "Error copying WAV file to cache")
            }
        }
        return destinationFile.path
    }


    private fun startRecording(callback: (ByteArray) -> Unit) {
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
                prepare()
                start()
            }
            isRecording = true
            playAudioButton.text = "Stop Recording"
            Toast.makeText(applicationContext, "Recording started", Toast.LENGTH_SHORT).show()

            callback(byteArrayOf())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        mediaRecorder.apply {
            stop()
            release()
        }
        isRecording = false
        playAudioButton.text = "Start Recording"
        Toast.makeText(applicationContext, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun processAudioData(audioData: ByteArray) {

    }





    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            val language = Locale.US
            val result = textToSpeech.setLanguage(language)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
            } else {
                val slowRate = 0.7f
                val normalPitch = 1.0f
                textToSpeech.setSpeechRate(slowRate)
                textToSpeech.setPitch(normalPitch)
            }
        } else {
        }
    }

    private fun askNextQuestion() {
        if (currentQuestionIndex < selectedQuestions.size) {
            val questionToAsk = selectedQuestions[currentQuestionIndex]
            textToSpeech.speak(questionToAsk, TextToSpeech.QUEUE_FLUSH, null, null)
            currentQuestionIndex++

            startQuestionTimer()
        } else {
            currentQuestionIndex = 0
        }
    }

    private fun startQuestionTimer() {
        questionTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
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

            val selectedQuestionsString = selectedQuestions.joinToString("\n")
            questionsTextView.text = selectedQuestionsString

            askNextQuestion()
        } else {
            questionsTextView.text = "Invalid input or not enough questions available."
        }
    }

    override fun onDestroy() {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()

        if (::questionTimer.isInitialized) {
            questionTimer.cancel()
        }

        super.onDestroy()
    }}
