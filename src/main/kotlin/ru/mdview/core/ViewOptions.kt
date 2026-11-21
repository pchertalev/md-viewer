package ru.mdview.core

/** Тема оформления страницы. AUTO берёт системную настройку Windows. */
enum class Theme {
    LIGHT,
    DARK,
    AUTO,
    ;

    companion object {
        fun fromId(id: String?): Theme =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: AUTO
    }
}

/**
 * Настройки просмотра. Меняются в окне и не влияют на сам markdown — файл при этом
 * остаётся нетронутым, меняется только сгенерированная страница.
 */
data class ViewOptions(
    val theme: Theme = Theme.AUTO,
    val fontScale: Double = 1.0,
    val maxWidthCh: Int = 84,
    val showToc: Boolean = true,
) {
    init {
        require(fontScale in MIN_SCALE..MAX_SCALE) { "Масштаб шрифта вне диапазона: $fontScale" }
        require(maxWidthCh in 40..200) { "Ширина текста вне диапазона: $maxWidthCh" }
    }

    companion object {
        const val MIN_SCALE = 0.6
        const val MAX_SCALE = 2.4
    }
}
