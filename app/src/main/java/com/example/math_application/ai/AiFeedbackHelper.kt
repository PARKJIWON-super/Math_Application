package com.example.math_application.ai

import com.example.math_application.ui.PauseRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object AiFeedbackHelper {
    // [변경] 노출된 키는 폐기하고, Google AI Studio에서 새로 발급한 키를 여기에 넣으세요.
    // 이 파일을 GitHub 등에 올리지 마세요. (이후 BuildConfig 또는 서버로 분리 예정)
    const val API_KEY = ""

    // 무료 티어 지원 Flash/Flash-Lite 계열 모델 (필요시 AI Studio 목록의 모델명으로 변경)
    private const val MODEL_NAME = "gemini-3.5-flash-lite"
    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    data class FeedbackResult(
        val success: Boolean,
        val message: String,
        val error: String? = null
    )

    // [변경] itemNumber -> pageNumber (실제로 페이지 단위 데이터이므로)
    data class ItemFeedbackInput(
        val pageNumber: Int,
        val strokeCount: Int,
        val pauseCount: Int,
        val maxPauseMs: Long,
        val pauseRecords: List<PauseRecord> = emptyList()
    )

    fun generateFeedback(
        problemTitle: String,
        problemDescription: String,
        strokeCount: Int,
        pauseCount: Int,
        maxPauseMs: Long, // [변경] lastPauseMs -> maxPauseMs
        pauseRecords: List<PauseRecord>
    ): FeedbackResult {
        if (API_KEY.isBlank() || API_KEY == "YOUR_GEMINI_API_KEY_HERE") {
            return FeedbackResult(
                success = false,
                message = "",
                error = "API 키가 설정되지 않았습니다. AiFeedbackHelper.kt의 API_KEY 상수에 Google AI Studio 키를 넣으세요."
            )
        }

        val prompt = buildSinglePrompt(
            problemTitle = problemTitle,
            problemDescription = problemDescription,
            strokeCount = strokeCount,
            pauseCount = pauseCount,
            maxPauseMs = maxPauseMs,
            pauseRecords = pauseRecords
        )

        return requestGemini(prompt = prompt, maxOutputTokens = 300)
    }

    fun generateMultiItemFeedback(
        examTitle: String,
        items: List<ItemFeedbackInput>
    ): FeedbackResult {
        if (API_KEY.isBlank() || API_KEY == "YOUR_GEMINI_API_KEY_HERE") {
            return FeedbackResult(
                success = false,
                message = "",
                error = "API 키가 설정되지 않았습니다. AiFeedbackHelper.kt의 API_KEY 상수에 Google AI Studio 키를 넣으세요."
            )
        }

        if (items.isEmpty()) {
            return FeedbackResult(
                success = false,
                message = "",
                error = "분석할 페이지별 풀이 기록이 없습니다."
            )
        }

        val prompt = buildMultiItemPrompt(
            examTitle = examTitle,
            items = items.sortedBy { it.pageNumber }
        )

        return requestGemini(prompt = prompt, maxOutputTokens = 1024)
    }

    private fun requestGemini(
        prompt: String,
        maxOutputTokens: Int
    ): FeedbackResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("x-goog-api-key", API_KEY)
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val partObj = JSONObject().apply {
                put("text", prompt)
            }
            val contentObj = JSONObject().apply {
                put("parts", JSONArray().put(partObj))
            }
            val generationConfig = JSONObject().apply {
                put("maxOutputTokens", maxOutputTokens)
                put("temperature", 0.7)
            }
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(contentObj))
                put("generationConfig", generationConfig)
            }

            val outputStream: OutputStream = connection.outputStream
            outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val content = extractContent(response)
                FeedbackResult(success = true, message = content)
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: "Unknown error"
                FeedbackResult(success = false, message = "", error = "HTTP $responseCode: $errorBody")
            }
        } catch (e: Exception) {
            FeedbackResult(success = false, message = "", error = "네트워크 오류: ${e.message}")
        } finally {
            // [추가] 연결 정리
            connection?.disconnect()
        }
    }

    private fun buildSinglePrompt(
        problemTitle: String,
        problemDescription: String,
        strokeCount: Int,
        pauseCount: Int,
        maxPauseMs: Long,
        pauseRecords: List<PauseRecord>
    ): String {
        val pauseSummary = formatPauseRecords(pauseRecords)

        // [변경] "최근 멈춤 시간"(실제로는 최대값이 들어가던 항목)을 제거하고 최대 멈춤 시간만 전달
        return """
            다음은 학생의 수학 문제 풀이 메타인지 데이터입니다.

            문제: $problemTitle
            내용: $problemDescription

            메타인지 데이터:
            - 총 획 수: $strokeCount
            - 총 멈춤 횟수: $pauseCount
            - 최대 멈춤 시간: $maxPauseMs ms
            - 구간별 멈춤: $pauseSummary

            (멈춤은 펜 입력이 1초 이상 없었던 구간입니다.)

            이 학생의 풀이 과정에서의 메타인지 상태와 개선점을 한국어로 3문장 이내로 간단히 피드백해주세요.
            마크다운 기호(**, #, - 등)는 쓰지 말고 일반 텍스트로만 작성하세요.
        """.trimIndent()
    }

    private fun buildMultiItemPrompt(
        examTitle: String,
        items: List<ItemFeedbackInput>
    ): String {
        val totalStrokes = items.sumOf { it.strokeCount }
        val totalPauses = items.sumOf { it.pauseCount }
        val overallMaxPause = items.maxOfOrNull { it.maxPauseMs } ?: 0L

        val itemsDetail = items.joinToString("\n") { item ->
            val pauseDetail = formatPauseRecords(item.pauseRecords)
            "- ${item.pageNumber}페이지: 획 ${item.strokeCount}개, 멈춤 ${item.pauseCount}회, 최대 멈춤 ${item.maxPauseMs}ms, 구간별 멈춤 [$pauseDetail]"
        }

        // [변경] 번호별 -> 페이지별, 용어 설명 추가, 마크다운 금지
        return """
            당신은 수학 학습자의 풀이 행동 로그를 분석하는 메타인지 학습 코치입니다.
            반드시 아래 제공된 페이지별 수치 데이터에 근거해서만 분석하고, 없는 수치를 지어내지 마세요.

            [시험/풀이 세트]: $examTitle
            [전체 합산]: 기록된 페이지 ${items.size}개, 총 획 ${totalStrokes}개, 총 멈춤 ${totalPauses}회, 전체 최대 멈춤 ${overallMaxPause}ms

            [용어]
            - 멈춤: 펜 입력이 1초 이상 없었던 구간
            - "N번째 획 뒤": 해당 페이지에서 N번째 획을 마친 뒤 다음 입력이 들어오기까지 걸린 시간
            - "첫 획 전": 해당 페이지의 첫 획을 쓰기 전에 발생한 멈춤

            [페이지별 데이터]
            $itemsDetail

            아래 형식에 맞춰 한국어 일반 텍스트로만 작성하세요. 마크다운 기호(**, #, - 등)는 사용하지 마세요.

            [페이지별 피드백]
            기록된 각 페이지마다 획 수, 멈춤 횟수, 최대 멈춤 시간을 바탕으로 풀이 흐름과 막힘/망설임 포인트를 1~2문장씩 짧게 피드백하세요. (예: 1페이지: ...)

            [전체 정리]
            전체 페이지를 통틀어 나타난 풀이 패턴, 주의가 필요한 페이지, 다음 풀이에서 실천할 핵심 개선점을 3~4문장으로 정리하세요.
        """.trimIndent()
    }

    // [변경] strokeId의 의미(해당 페이지에서 직전까지 완성된 획 번호)가 AI에게 그대로 전달되도록 문구 변경
    private fun formatPauseRecords(pauseRecords: List<PauseRecord>): String {
        return if (pauseRecords.isNotEmpty()) {
            pauseRecords.joinToString(", ") {
                if (it.strokeId == 0) {
                    "첫 획 전 ${it.pauseMs}ms"
                } else {
                    "${it.strokeId}번째 획 뒤 ${it.pauseMs}ms"
                }
            }
        } else {
            "기록 없음"
        }
    }

    private fun extractContent(json: String): String {
        return try {
            val root = JSONObject(json)
            val candidates = root.optJSONArray("candidates") ?: return json
            if (candidates.length() == 0) return "응답 후보가 비어 있습니다."
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return json
            val parts = content.optJSONArray("parts") ?: return json
            if (parts.length() == 0) return "응답 텍스트가 비어 있습니다."
            parts.getJSONObject(0).optString("text", json).trim()
        } catch (e: Exception) {
            json
        }
    }
}
