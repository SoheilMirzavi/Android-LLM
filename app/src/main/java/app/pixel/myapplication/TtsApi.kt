package app.pixel.myapplication

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TtsApi {
    @POST("tts")
    @Headers("Content-Type: application/json")
    suspend fun generateSpeech(@Body requestBody: Map<String, String>): Response<ResponseBody>

    @Multipart
    @POST("upload")
    suspend fun uploadAudio(@Part audio: MultipartBody.Part): Response<AudioResponse>
}

data class AudioResponse(
    val recognized_text: String,
    val response: String,
    val file_content_base64: String
)