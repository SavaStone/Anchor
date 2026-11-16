package app.opora.domain

data class MoodStats(
    val count: Int = 0,
    val avgMood: Double? = null,
    val minMood: Int? = null,
    val maxMood: Int? = null,
    val avgAnxiety: Double? = null,
    val avgEnergy: Double? = null,
    val avgSleepMinutes: Int? = null,
    val streak: Int = 0,
    val distribution: Map<Int, Int> = emptyMap(),
    val topEmotions: List<Pair<String, Int>> = emptyList(),
    val topSymptoms: List<Pair<String, Int>> = emptyList()
)
