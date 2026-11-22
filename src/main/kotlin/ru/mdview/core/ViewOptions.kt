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
    val maxWidthCh: Int = FULL_WIDTH,
    val showToc: Boolean = true,
) {
    init {
        require(fontScale in MIN_SCALE..MAX_SCALE) { "Масштаб шрифта вне диапазона: $fontScale" }
        require(maxWidthCh == FULL_WIDTH || maxWidthCh in MIN_WIDTH..MAX_WIDTH) {
            "Ширина текста вне диапазона: $maxWidthCh"
        }
    }

    companion object {
        const val MIN_SCALE = 0.6
        const val MAX_SCALE = 2.4

        const val MIN_WIDTH = 40
        const val MAX_WIDTH = 200

        /** Без ограничения: на широком окне узкая колонка выглядит пустой. */
        const val FULL_WIDTH = 0

        /** Порядок переключения ширины в меню. */
        val WIDTH_PRESETS = listOf(FULL_WIDTH, 84, 120)
    }
}
